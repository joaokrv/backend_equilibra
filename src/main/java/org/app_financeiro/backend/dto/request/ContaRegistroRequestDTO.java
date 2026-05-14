package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO para requisição de registro de uma nova conta bancária.
 */
public record ContaRegistroRequestDTO(
    @NotBlank(message = "O nome da conta é obrigatório")
    @Size(max = 100, message = "O nome da conta não pode exceder 100 caracteres")
    String nome,

    BigDecimal saldo
) {}
