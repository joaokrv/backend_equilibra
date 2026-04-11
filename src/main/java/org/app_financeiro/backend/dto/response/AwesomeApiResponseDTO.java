package org.app_financeiro.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * DTO para representar a resposta da AwesomeAPI (Cotações de Moedas).
 * A API retorna um Map dinâmico por par (ex: "USDBRL").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AwesomeApiResponseDTO(
    Map<String, CurrencyInfoDTO> rates
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrencyInfoDTO(
        String code,
        String codein,
        String name,
        String bid, // Valor de compra (cotação atual)
        
        @JsonProperty("pctChange")
        String pctChange, // Variação percentual
        
        String high,
        String low,
        
        @JsonProperty("create_date")
        String createDate
    ) {}
}
