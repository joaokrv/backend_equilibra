package org.app_financeiro.backend.enums;

public enum FormatoDetectado {
    /** Extrato CSV de qualquer banco — estrutura detectada dinamicamente; fallback via IA. */
    CSV,
    PDF_FATURA,
    PDF_EXTRATO
}
