package org.app_financeiro.backend.dto.response;

import java.math.BigDecimal;

/** DTO de resposta com os dados de uma conta bancária do usuário. */
public record ContaResponseDTO(
        Long id,
        String nome,
        BigDecimal saldo
) {}
