package org.app_financeiro.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.app_financeiro.backend.dto.response.BrapiResponseDTO;
import org.app_financeiro.backend.dto.response.MercadoIndicadoresResponseDTO;
import org.app_financeiro.backend.service.MarketDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Controller unificado de dados de mercado.
 *
 * Endpoints:
 * - GET /api/mercado/cotacoes      → Cotações B3 (proxy Brapi)
 * - GET /api/mercado/cambio        → Taxas de câmbio (proxy AwesomeAPI)
 * - GET /api/mercado/indicadores   → Indicadores macro do banco (SELIC, CDI, IPCA, IBOVESPA, IFIX)
 */
@Slf4j
@RestController
@RequestMapping("/api/mercado")
@RequiredArgsConstructor
@Tag(name = "Mercado", description = "Terminal de cotações, câmbio e indicadores econômicos")
public class MercadoController {

    private final MarketDataService marketDataService;

    @GetMapping("/cotacoes")
    @Operation(summary = "Buscar cotações de ativos", description = "Retorna os preços atuais e variação percentual de ativos da B3.")
    public ResponseEntity<BrapiResponseDTO> getCotacoes(@RequestParam String tickers) {
        List<String> tickersList = Arrays.asList(tickers.split(","));
        return ResponseEntity.ok(marketDataService.getQuotes(tickersList));
    }

    @GetMapping("/cambio")
    @Operation(summary = "Buscar taxas de câmbio", description = "Retorna a cotação atual e variação para o par de moedas informado.")
    public ResponseEntity<Map<String, Object>> getCambio(@RequestParam(defaultValue = "USD-BRL") String pair) {
        return ResponseEntity.ok(marketDataService.getExchangeRates(pair));
    }

    @GetMapping("/indicadores")
    @Operation(summary = "Buscar indicadores consolidados", description = "Retorna SELIC, CDI, IPCA, câmbio e índices (IBOVESPA, IFIX) consolidados do banco de dados.")
    public ResponseEntity<MercadoIndicadoresResponseDTO> getIndicadores() {
        log.debug("Endpoint /api/mercado/indicadores acessado.");
        MercadoIndicadoresResponseDTO response = marketDataService.getIndicadoresConsolidados();
        return ResponseEntity.ok(response);
    }
}
