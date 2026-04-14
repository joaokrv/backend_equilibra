package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.HgFinanceResponseDTO;
import org.app_financeiro.backend.repository.IndicadorEconomicoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Teste de unidade do MarketDataService.
 * Valida a lógica de sincronização e mapeamento de indicadores sem dependência de banco.
 */
@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @InjectMocks
    private MarketDataService marketDataService;

    @Mock
    private IndicadorEconomicoRepository indicadorRepository;

    @Mock
    private HgFinanceClient hgFinanceClient;

    @Mock
    private RestTemplate restTemplate; // Necessário para o construtor do service

    @Test
    @DisplayName("Deve processar e salvar indicadores macroeconômicos corretamente")
    void deveProcessarESalvarIndicadores() {
        // GIVEN
        HgFinanceResponseDTO.TaxDTO tax = new HgFinanceResponseDTO.TaxDTO("2024-03-29", new BigDecimal("10.65"), new BigDecimal("10.75"), null, null, null);
        HgFinanceResponseDTO.Results results = new HgFinanceResponseDTO.Results(
            Map.of("USD", new HgFinanceResponseDTO.CurrencyDTO("Dollar", new BigDecimal("5.48"), null, new BigDecimal("0.12"))),
            List.of(tax),
            null // stocks não testado neste cenário
        );
        HgFinanceResponseDTO response = new HgFinanceResponseDTO(true, results);

        when(hgFinanceClient.fetchFinanceData()).thenReturn(Optional.of(response));

        // WHEN
        marketDataService.syncIndicadoresMacro();

        // THEN
        verify(indicadorRepository).insertIndicador(
            eq("SELIC"),
            argThat(valor -> valor != null && valor.compareTo(new BigDecimal("10.75")) == 0),
            isNull(),
            any(),
            eq("HG_BRASIL")
        );
        verify(indicadorRepository).insertIndicador(
            eq("CDI"),
            argThat(valor -> valor != null && valor.compareTo(new BigDecimal("10.65")) == 0),
            isNull(),
            any(),
            eq("HG_BRASIL")
        );
        verify(indicadorRepository).insertIndicador(
            eq("USD"),
            argThat(valor -> valor != null && valor.compareTo(new BigDecimal("5.48")) == 0),
            argThat(variacao -> variacao != null && variacao.compareTo(new BigDecimal("0.12")) == 0),
            any(),
            eq("HG_BRASIL")
        );
    }
}
