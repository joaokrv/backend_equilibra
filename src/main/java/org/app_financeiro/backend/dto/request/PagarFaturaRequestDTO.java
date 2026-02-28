package org.app_financeiro.backend.dto.request;

import lombok.Data;

/**
 * DTO para requisição de pagamento de fatura.
 * Atualmente vazio, mas pode ser expandido para incluir 
 * a conta de onde o pagamento será debitado, data do pagamento, etc.
 */
@Data
public class PagarFaturaRequestDTO {
    // Pode incluir campos como:
    // private Long contaId;
    // private LocalDate dataPagamento;
    // private BigDecimal valorPago;
}