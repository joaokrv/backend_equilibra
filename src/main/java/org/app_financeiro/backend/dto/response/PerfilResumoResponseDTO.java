package org.app_financeiro.backend.dto.response;

import java.math.BigDecimal;

/**
 * DTO que consolida o balanço financeiro geral do usuário para exibição no perfil.
 */
public record PerfilResumoResponseDTO(
    BigDecimal totalReceitas,
    BigDecimal totalDespesas,
    BigDecimal saldoContas,
    BigDecimal totalInvestido,
    Double progressoMetas
) {}
