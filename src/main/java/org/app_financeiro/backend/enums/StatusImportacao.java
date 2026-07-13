package org.app_financeiro.backend.enums;

public enum StatusImportacao {
    /** Candidatas extraídas, aguardando revisão do usuário. */
    PENDENTE,
    /** Confirmação em andamento (claim atômico) — evita lote duplo concorrente. */
    PROCESSANDO,
    /** Usuário confirmou — transações criadas. */
    CONFIRMADA,
    /** Usuário cancelou ou sessão expirada. */
    CANCELADA
}
