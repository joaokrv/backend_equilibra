package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InvestimentoRegistroRequestDTO {

    @NotBlank(message = "A descrição do investimento é obrigatória")
    private String descricao;

    @NotNull(message = "O valor inicial é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor inicial deve ser maior que zero")
    private BigDecimal valorInicial;

    @NotNull(message = "A meta é obrigatória")
    @DecimalMin(value = "0.01", message = "A meta deve ser maior que zero")
    private BigDecimal meta;
}
