package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO para requisição de pagamento de fatura.
 */
@Data
public class PagarFaturaRequestDTO {
    
    @NotNull(message = "O ID da conta é obrigatório")
    private Long contaId;
    
    @NotNull(message = "O valor do pagamento é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor do pagamento deve ser maior que zero")
    private BigDecimal valorPago;
}