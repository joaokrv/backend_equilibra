package org.app_financeiro.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.app_financeiro.backend.dto.response.BrapiResponseDTO;
import org.app_financeiro.backend.dto.response.HgFinanceResponseDTO;
import org.app_financeiro.backend.dto.response.MercadoIndicadoresResponseDTO;
import org.app_financeiro.backend.entity.IndicadorEconomicoEntity;
import org.app_financeiro.backend.repository.IndicadorEconomicoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Proxy para APIs financeiras externas (Brapi, AwesomeAPI, HG Brasil, BCB) com cache em memória. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private final RestTemplate restTemplate;
    private final HgFinanceClient hgFinanceClient;
    private final IndicadorEconomicoRepository indicadorRepository;

    @Value("${brapi.token:}")
    private String brapiToken;

    @Value("${awesome-api.base-url:https://economia.awesomeapi.com.br}")
    private String awesomeApiBaseUrl;

    @Value("${awesome-api.key:}")
    private String awesomeApiKey;

    private final Map<String, CachedData<BrapiResponseDTO.StockResultDTO>> quotesCache = new ConcurrentHashMap<>();
    private final Map<String, CachedData<Map<String, Object>>> exchangeCache = new ConcurrentHashMap<>();

    private static final int CACHE_MINUTES_STOCKS = 15;
    private static final int CACHE_HOURS_CURRENCY = 12;

    private static final Set<String> PARES_PERMITIDOS = Set.of(
            "USD-BRL", "EUR-BRL", "GBP-BRL", "USD-BRL,EUR-BRL"
    );

    public BrapiResponseDTO getQuotes(List<String> tickers) {
        evictExpiredEntries(quotesCache);
        List<BrapiResponseDTO.StockResultDTO> results = new ArrayList<>();

        for (String ticker : tickers) {
            if (isCacheValid(quotesCache.get(ticker))) {
                log.debug("Retornando cotação do cache para: {}", ticker);
                results.add(quotesCache.get(ticker).data());
                continue;
            }

            if (ticker == null || !ticker.matches("^[A-Z0-9.]{1,20}$")) {
                log.warn("Tentativa de consulta com ticker inválido ou malicioso: {}", ticker);
                continue; 
            }

            try {
                String url = UriComponentsBuilder.fromHttpUrl("https://brapi.dev/api/quote/")
                        .path(ticker)
                        .queryParam("token", brapiToken)
                        .toUriString();

                log.debug("Buscando cotação na Brapi: {}", ticker);
                BrapiResponseDTO response = restTemplate.getForObject(url, BrapiResponseDTO.class);
                
                if (response != null && response.results() != null && !response.results().isEmpty()) {
                    BrapiResponseDTO.StockResultDTO stockData = response.results().get(0);
                    quotesCache.put(ticker, new CachedData<>(stockData, LocalDateTime.now().plusMinutes(CACHE_MINUTES_STOCKS)));
                    results.add(stockData);
                }
            } catch (Exception e) {
                log.error("Erro ao buscar cotação para {} na Brapi: {}", ticker, e.getMessage());
            }
        }
        
        return new BrapiResponseDTO(results);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getExchangeRates(String pair) {
        if (!PARES_PERMITIDOS.contains(pair)) {
            log.warn("Par de câmbio não permitido rejeitado: {}", pair);
            return Map.of();
        }

        evictExpiredEntries(exchangeCache);
        if (isCacheValid(exchangeCache.get(pair))) {
            return exchangeCache.get(pair).data();
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(awesomeApiBaseUrl + "/last/" + pair);
            if (awesomeApiKey != null && !awesomeApiKey.isBlank()) {
                builder.queryParam("token", awesomeApiKey);
            }
            String url = builder.toUriString();
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                exchangeCache.put(pair, new CachedData<>(response, LocalDateTime.now().plusHours(CACHE_HOURS_CURRENCY)));
                return response;
            }
        } catch (Exception e) {
            log.error("Erro ao buscar câmbio na AwesomeAPI: {}", e.getMessage());
        }

        return Map.of();
    }

    @Transactional
    public void syncIndicadoresMacro() {
        log.debug("Iniciando sincronização de indicadores macroeconômicos...");
        
        hgFinanceClient.fetchFinanceData().ifPresentOrElse(
            this::processarRespostaHg,
            () -> log.warn("Falha ao sincronizar com HG Brasil. Mantendo últimos valores conhecidos.")
        );
    }

    private void processarRespostaHg(HgFinanceResponseDTO response) {
        HgFinanceResponseDTO.Results results = response.results();
        LocalDate hoje = LocalDate.now();

        if (results.taxes() != null && !results.taxes().isEmpty()) {
            HgFinanceResponseDTO.TaxDTO taxes = results.taxes().get(0);
            salvarIndicador("SELIC", taxes.selic(), null, hoje, "HG_BRASIL");
            salvarIndicador("CDI", taxes.cdi(), null, hoje, "HG_BRASIL");
        }

        if (results.currencies() != null) {
            results.currencies().forEach((key, data) -> {
                if (List.of("USD", "EUR").contains(key)) {
                    salvarIndicador(key, data.buy(), data.variation(), hoje, "HG_BRASIL");
                }
            });
        }

        if (results.stocks() != null) {
            results.stocks().forEach((key, data) -> {
                if (List.of("IBOVESPA", "IFIX", "BITCOIN", "NASDAQ", "DOWJONES").contains(key) && data.points() != null) {
                    salvarIndicador(key, data.points(), data.variation(), hoje, "HG_BRASIL");
                }
            });
        }
    }

    public MercadoIndicadoresResponseDTO getIndicadoresConsolidados() {
        List<IndicadorEconomicoEntity> indicadores = indicadorRepository.buscarUltimosIndicadores();

        Map<String, BigDecimal> taxas = new HashMap<>();
        Map<String, MercadoIndicadoresResponseDTO.MoedaInfoDTO> moedas = new HashMap<>();
        Map<String, MercadoIndicadoresResponseDTO.MoedaInfoDTO> indices = new HashMap<>();

        for (IndicadorEconomicoEntity i : indicadores) {
            if (List.of("SELIC", "CDI", "IPCA").contains(i.getNome())) {
                taxas.put(i.getNome(), i.getValor());
            } else if (List.of("USD", "EUR").contains(i.getNome())) {
                moedas.put(i.getNome(), new MercadoIndicadoresResponseDTO.MoedaInfoDTO(i.getValor(), i.getVariacao()));
            } else if (List.of("IBOVESPA", "IFIX", "BITCOIN", "NASDAQ", "DOWJONES").contains(i.getNome())) {
                indices.put(i.getNome(), new MercadoIndicadoresResponseDTO.MoedaInfoDTO(i.getValor(), i.getVariacao()));
            }
        }

        return new MercadoIndicadoresResponseDTO(taxas, moedas, indices);
    }

    @Transactional
    public void syncIPCA() {
        log.debug("Buscando IPCA na API do Banco Central (SGS série 13522)...");
        try {
            String url = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.13522/dados/ultimos/1?formato=json";
            List<?> response = restTemplate.getForObject(url, List.class);

            if (response != null && !response.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, String> dado = (Map<String, String>) response.get(0);
                BigDecimal valor = new BigDecimal(dado.get("valor"));
                salvarIndicador("IPCA", valor, null, LocalDate.now(), "BCB_SGS");
            }
        } catch (Exception e) {
            log.warn("Falha ao buscar IPCA no BCB SGS: {}", e.getMessage());
        }
    }

    @Transactional
    public void syncSelicBCB() {
        log.debug("Buscando meta SELIC na API do Banco Central (SGS série 1178)...");
        try {
            String url = "https://api.bcb.gov.br/dados/serie/bcdata.sgs.1178/dados/ultimos/1?formato=json";
            List<?> response = restTemplate.getForObject(url, List.class);

            if (response != null && !response.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, String> dado = (Map<String, String>) response.get(0);
                BigDecimal valor = new BigDecimal(dado.get("valor"));
                salvarIndicador("SELIC", valor, null, LocalDate.now(), "BCB_SGS");
            }
        } catch (Exception e) {
            log.warn("Falha ao buscar meta SELIC no BCB SGS: {}", e.getMessage());
        }
    }

    private void salvarIndicador(String nome, BigDecimal valor, BigDecimal variacao, LocalDate data, String provedor) {
        try {
            IndicadorEconomicoEntity indicador = new IndicadorEconomicoEntity(nome, valor, variacao, data, provedor);
            indicadorRepository.save(indicador);
            log.debug("Indicador {} atualizado: {} (Provedor: {})", nome, valor, provedor);
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Falha de infraestrutura ao salvar indicador {}: {} (Ignorando para evitar crash)", nome, e.getMessage());
        }
    }

    private boolean isCacheValid(CachedData<?> cached) {
        return cached != null && cached.expiration().isAfter(LocalDateTime.now());
    }

    private <T> void evictExpiredEntries(Map<String, CachedData<T>> cache) {
        LocalDateTime now = LocalDateTime.now();
        cache.entrySet().removeIf(entry -> entry.getValue().expiration().isBefore(now));
    }

    private record CachedData<T>(T data, LocalDateTime expiration) {}
}
