package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO para requisição de pagamento de fatura.
 */
public record PagarFaturaRequestDTO(
    @NotNull(message = "O ID da conta é obrigatório")
    Long contaId,

    @NotNull(message = "O valor do pagamento é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor do pagamento deve ser maior que zero")
    BigDecimal valorPago
) {}
