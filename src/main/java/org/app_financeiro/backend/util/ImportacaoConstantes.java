package org.app_financeiro.backend.util;

/** Limites compartilhados da importação de documentos (parser, service e validação dos DTOs). */
public final class ImportacaoConstantes {

    private ImportacaoConstantes() {}

    /** Teto de transações por documento — anti-abuso e teto dos @Size dos DTOs de confirmação. */
    public static final int MAX_CANDIDATAS = 500;
}
