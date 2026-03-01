package org.app_financeiro.backend.enums;

/**
 * Enum que representa o ciclo de vida de uma FaturaEntity.
 *
 * Fluxo de transições válidas:
 * ABERTA: fatura do mês atual, ainda aceitando transações.
 * FECHADA: passou o dia de fechamento do cartão, não aceita mais transações.
 * ATRASADA: passou o dia de vencimento sem pagamento integral.
 * PAGA: fatura quitada integralmente via FaturaService.pagarFatura().
 *
 * O FaturaService aplica "Ghost Closing" ao listar faturas:
 * atualiza os status ABERTA e FECHADA automaticamente com base na data atual,
 * sem necessidade de agendamento (scheduler).
 */
public enum StatusFatura {
    ABERTA,
    FECHADA,
    PAGA,
    ATRASADA
}
