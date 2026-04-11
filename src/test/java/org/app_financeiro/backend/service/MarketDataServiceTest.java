package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.HgFinanceResponseDTO;
import org.app_financeiro.backend.entity.IndicadorEconomicoEntity;
import org.app_financeiro.backend.repository.IndicadorEconomicoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
        ArgumentCaptor<IndicadorEconomicoEntity> captor = ArgumentCaptor.forClass(IndicadorEconomicoEntity.class);
        verify(indicadorRepository, atLeast(3)).save(captor.capture());

        List<IndicadorEconomicoEntity> salvos = captor.getAllValues();
        
        assertTrue(salvos.stream().anyMatch(i -> i.getNome().equals("SELIC") && i.getValor().equals(new BigDecimal("10.75"))));
        assertTrue(salvos.stream().anyMatch(i -> i.getNome().equals("CDI") && i.getValor().equals(new BigDecimal("10.65"))));
        assertTrue(salvos.stream().anyMatch(i -> i.getNome().equals("USD") && i.getValor().equals(new BigDecimal("5.48"))));
    }
}
