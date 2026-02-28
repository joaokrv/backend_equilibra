package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ContaRegistroRequestDTO {

    @NotBlank(message = "O nome da conta é obrigatório")
    private String nome;

    // Pode ser nulo (nós assumimos 0,00 no Service se vier vazio)
    private BigDecimal saldo;
}
