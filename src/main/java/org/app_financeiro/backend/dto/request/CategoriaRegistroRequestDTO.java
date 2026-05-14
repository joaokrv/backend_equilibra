package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.app_financeiro.backend.enums.TipoTransacao;

/**
 * DTO para requisição de registro de uma nova categoria de transação.
 */
public record CategoriaRegistroRequestDTO(
    @NotBlank(message = "O nome da categoria é obrigatório")
    @Size(max = 100, message = "O nome da categoria não pode exceder 100 caracteres")
    String nome,

    @NotNull(message = "O tipo da categoria (RECEITA/DESPESA) é obrigatório")
    TipoTransacao tipo
) {}
