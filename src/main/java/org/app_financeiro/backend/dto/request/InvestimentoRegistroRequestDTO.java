package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO para requisição de registro de um novo investimento ou meta de poupança.
 */
public record InvestimentoRegistroRequestDTO(
    @NotBlank(message = "A descrição do investimento é obrigatória")
    String descricao,

    @NotNull(message = "O valor inicial é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor inicial deve ser maior que zero")
    BigDecimal valorInicial,

    @NotNull(message = "A meta é obrigatória")
    @DecimalMin(value = "0.01", message = "A meta deve ser maior que zero")
    BigDecimal meta
) {}
