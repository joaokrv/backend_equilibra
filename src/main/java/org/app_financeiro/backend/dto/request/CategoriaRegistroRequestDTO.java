package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.app_financeiro.backend.enums.TipoTransacao;

@Data
public class CategoriaRegistroRequestDTO {

    @NotBlank(message = "O nome da categoria é obrigatório")
    private String nome;

    @NotNull(message = "O tipo da categoria (RECEITA/DESPESA) é obrigatório")
    private TipoTransacao tipo;
}
