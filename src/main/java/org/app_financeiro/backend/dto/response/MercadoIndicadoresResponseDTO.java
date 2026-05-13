package org.app_financeiro.backend.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * DTO consolidado para exposição de indicadores de mercado.
 * Utilizado para alimentar a barra dinâmica do Dashboard.
 */
public record MercadoIndicadoresResponseDTO(
    Map<String, BigDecimal> taxas,
    Map<String, MoedaInfoDTO> moedas,
    Map<String, MoedaInfoDTO> indices
) {
    public record MoedaInfoDTO(
        BigDecimal valor,
        BigDecimal variacao
    ) {}
}
