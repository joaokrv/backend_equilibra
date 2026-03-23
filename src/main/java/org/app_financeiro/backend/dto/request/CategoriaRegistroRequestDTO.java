package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.app_financeiro.backend.enums.TipoTransacao;

/**
 * DTO para requisição de registro de uma nova categoria de transação.
 */
public record CategoriaRegistroRequestDTO(
    @NotBlank(message = "O nome da categoria é obrigatório")
    String nome,

    @NotNull(message = "O tipo da categoria (RECEITA/DESPESA) é obrigatório")
    TipoTransacao tipo
) {}
