package org.app_financeiro.backend.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO consolidado para exposição de indicadores de mercado.
 * Utilizado para alimentar a barra dinâmica do Dashboard.
 */
public record MercadoIndicadoresResponseDTO(
    Map<String, BigDecimal> taxas,      // SELIC, CDI, IPCA
    Map<String, MoedaInfoDTO> moedas,   // USD, EUR (valor=cotação, variacao=%)
    Map<String, MoedaInfoDTO> indices   // IBOVESPA, IFIX (valor=pontos, variacao=%)
) {
    public record MoedaInfoDTO(
        BigDecimal valor,
        BigDecimal variacao
    ) {}
}
