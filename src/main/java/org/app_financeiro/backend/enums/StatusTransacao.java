package org.app_financeiro.backend.enums;

/**
 * Enum que representa o status de liquidação de uma transação.
 *
 * PENDENTE: lançamento registrado mas ainda não efetivado (ex: conta a pagar).
 * PAGO: transação já concluída e confirmada.
 *
 * O status default ao criar uma transação sem informar este campo é PENDENTE.
 */
public enum StatusTransacao {
    PAGO,
    PENDENTE
}
