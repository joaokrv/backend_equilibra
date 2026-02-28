package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartaoRegistroRequestDTO {

    @NotBlank(message = "O nome do cartão é obrigatório")
    private String nome;

    @NotNull(message = "O limite do cartão é obrigatório")
    @DecimalMin(value = "0", message = "O limite não pode ser negativo")
    private BigDecimal limite;

    @NotNull(message = "O dia de fechamento é obrigatório")
    @Min(value = 1, message = "O dia deve ser entre 1 e 31")
    @Max(value = 31, message = "O dia deve ser entre 1 e 31")
    private Integer diaFechamento;

    @NotNull(message = "O dia de vencimento é obrigatório")
    @Min(value = 1, message = "O dia deve ser entre 1 e 31")
    @Max(value = 31, message = "O dia deve ser entre 1 e 31")
    private Integer diaVencimento;
}
