package org.app_financeiro.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ponto de série temporal para evolução do patrimônio.
 * Representa o snapshot diário consolidado de contas + investimentos.
 */
public record PatrimonioEvolucaoResponseDTO(
        LocalDate dataReferencia,
        BigDecimal valorTotal
) {
}