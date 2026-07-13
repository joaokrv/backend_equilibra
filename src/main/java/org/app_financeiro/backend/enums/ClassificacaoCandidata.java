package org.app_financeiro.backend.enums;

/**
 * Classificação de uma candidata de importação, atribuída pelo parser (CSV),
 * pelo modelo de IA (PDF) ou pela heurística de enriquecimento — sempre revisável
 * pelo usuário na tela de revisão antes da confirmação.
 */
public enum ClassificacaoCandidata {
    /** Transação comum de receita/despesa. */
    NORMAL,
    /** Aplicação em investimento (ex.: "Aplicação CDB") — vinculável a um investimento na revisão. */
    APORTE,
    /** Resgate de investimento — vinculável a um investimento na revisão. */
    RESGATE,
    /** Movimentação entre contas do próprio usuário (ex.: Pix para si) — importa com isTransferencia=true. */
    TRANSFERENCIA_INTERNA
}
