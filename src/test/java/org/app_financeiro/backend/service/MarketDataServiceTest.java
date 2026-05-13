package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.HgFinanceResponseDTO;
import org.app_financeiro.backend.entity.IndicadorEconomicoEntity;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** Teste de unidade do MarketDataService. Valida sincronização e mapeamento sem banco. */
@ExtendWith(MockitoExtension.class)
class MarketDataServiceTest {

    @InjectMocks
    private MarketDataService marketDataService;

    @Mock
    private IndicadorEconomicoRepository indicadorRepository;

    @Mock
    private HgFinanceClient hgFinanceClient;

    @Mock
    private RestTemplate restTemplate;

    @Test
    @DisplayName("Deve processar e salvar indicadores macroeconômicos corretamente")
    void deveProcessarESalvarIndicadores() {
        HgFinanceResponseDTO.TaxDTO tax = new HgFinanceResponseDTO.TaxDTO(
            "2024-03-29",
            new BigDecimal("10.65"),
            new BigDecimal("10.75"),
            null, null, null
        );
        HgFinanceResponseDTO.Results results = new HgFinanceResponseDTO.Results(
            Map.of("USD", new HgFinanceResponseDTO.CurrencyDTO("Dollar", new BigDecimal("5.48"), null, new BigDecimal("0.12"))),
            List.of(tax),
            null
        );
        HgFinanceResponseDTO response = new HgFinanceResponseDTO(true, results);

        when(hgFinanceClient.fetchFinanceData()).thenReturn(Optional.of(response));

        marketDataService.syncIndicadoresMacro();

        verify(indicadorRepository).save(argThat((IndicadorEconomicoEntity e) ->
            "SELIC".equals(e.getNome()) &&
            new BigDecimal("10.75").compareTo(e.getValor()) == 0 &&
            e.getVariacao() == null &&
            "HG_BRASIL".equals(e.getProvedor())
        ));
        verify(indicadorRepository).save(argThat((IndicadorEconomicoEntity e) ->
            "CDI".equals(e.getNome()) &&
            new BigDecimal("10.65").compareTo(e.getValor()) == 0 &&
            e.getVariacao() == null &&
            "HG_BRASIL".equals(e.getProvedor())
        ));
        verify(indicadorRepository).save(argThat((IndicadorEconomicoEntity e) ->
            "USD".equals(e.getNome()) &&
            new BigDecimal("5.48").compareTo(e.getValor()) == 0 &&
            new BigDecimal("0.12").compareTo(e.getVariacao()) == 0 &&
            "HG_BRASIL".equals(e.getProvedor())
        ));
    }
}
