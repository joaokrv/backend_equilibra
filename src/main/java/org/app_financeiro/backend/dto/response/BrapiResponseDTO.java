package org.app_financeiro.backend.dto.response;

import java.util.List;

/**
 * DTO para representar a resposta da Brapi API (Ações B3).
 */
public record BrapiResponseDTO(
    List<StockResultDTO> results
) {
    public record StockResultDTO(
        String symbol,
        Double regularMarketPrice,
        Double regularMarketChangePercent,
        String longName,
        String logourl
    ) {}
}
