package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * DTO para requisição de registro de uma nova conta bancária.
 */
public record ContaRegistroRequestDTO(
    @NotBlank(message = "O nome da conta é obrigatório")
    String nome,

    BigDecimal saldo
) {}
