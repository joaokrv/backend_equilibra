package org.app_financeiro.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO Raiz para resposta da API HG Brasil Finance.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HgFinanceResponseDTO(
    @JsonProperty("valid_key") boolean validKey,
    Results results
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Results(
        Map<String, CurrencyDTO> currencies,
        List<TaxDTO> taxes,
        Map<String, StockIndexDTO> stocks
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockIndexDTO(
        String name,
        String location,
        BigDecimal points,
        BigDecimal variation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrencyDTO(
        String name,
        BigDecimal buy,
        BigDecimal sell,
        BigDecimal variation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaxDTO(
        String date,
        BigDecimal cdi,
        BigDecimal selic,
        @JsonProperty("daily_factor") BigDecimal dailyFactor,
        @JsonProperty("selic_daily") BigDecimal selicDaily,
        @JsonProperty("cdi_daily") BigDecimal cdiDaily
    ) {}
}
