package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.HgFinanceResponseDTO;
import org.app_financeiro.backend.entity.IndicadorEconomicoEntity;
import org.app_financeiro.backend.repository.IndicadorEconomicoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Teste de integração do MarketDataService.
 * Valida o ciclo completo de sincronização e persistência no banco de dados.
 */
@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
class MarketDataServiceIntegrationTest {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private IndicadorEconomicoRepository indicadorRepository;

    @MockitoBean
    private HgFinanceClient hgFinanceClient;

    @Test
    @DisplayName("Deve sincronizar e salvar indicadores macroeconômicos no banco")
    void deveSincronizarESalvarIndicadores() {
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
        List<IndicadorEconomicoEntity> salvos = indicadorRepository.findAll();
        assertFalse(salvos.isEmpty());
        
        assertTrue(salvos.stream().anyMatch(i -> i.getNome().equals("SELIC") && i.getValor().compareTo(new BigDecimal("10.75")) == 0));
        assertTrue(salvos.stream().anyMatch(i -> i.getNome().equals("USD") && i.getValor().compareTo(new BigDecimal("5.48")) == 0));
        
        verify(hgFinanceClient, times(1)).fetchFinanceData();
    }
}
