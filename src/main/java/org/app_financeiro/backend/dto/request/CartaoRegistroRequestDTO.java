package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO para requisição de registro de um novo cartão de crédito.
 */
public record CartaoRegistroRequestDTO(
    @NotBlank(message = "O nome do cartão é obrigatório")
    String nome,

    @NotNull(message = "O limite do cartão é obrigatório")
    @DecimalMin(value = "0", message = "O limite não pode ser negativo")
    BigDecimal limite,

    @NotNull(message = "O dia de fechamento é obrigatório")
    @Min(value = 1, message = "O dia deve ser entre 1 e 31")
    @Max(value = 31, message = "O dia deve ser entre 1 e 31")
    Integer diaFechamento,

    @NotNull(message = "O dia de vencimento é obrigatório")
    @Min(value = 1, message = "O dia deve ser entre 1 e 31")
    @Max(value = 31, message = "O dia deve ser entre 1 e 31")
    Integer diaVencimento
) {}
