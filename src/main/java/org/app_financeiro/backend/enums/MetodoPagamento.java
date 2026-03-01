package org.app_financeiro.backend.enums;

/**
 * Enum que representa os métodos de pagamento disponíveis para uma transação.
 * Campo opcional em TransacaoEntity — pode ser nulo quando não aplicável.
 */
public enum MetodoPagamento {
    CARTAO_CREDITO,
    PIX,
    VALE_ALIMENTACAO,
    DINHEIRO,
    TRANSFERENCIA,
    BOLETO,
    CARTAO_DEBITO
}
