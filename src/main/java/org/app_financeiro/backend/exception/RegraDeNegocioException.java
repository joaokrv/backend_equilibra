package org.app_financeiro.backend.exception;

/** Base para violações de regra de negócio nos services. → 422 UNPROCESSABLE_ENTITY (subclasses podem sobrescrever). */
public class RegraDeNegocioException extends RuntimeException {

    private final String messageKey;
    private final transient Object[] args;

    /** Mensagem crua (sem i18n). Mantida para compatibilidade. */
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
        this.messageKey = null;
        this.args = null;
    }

    /**
     * Mensagem internacionalizável: {@code messageKey} é resolvida via MessageSource pelo handler;
     * {@code defaultMessage} (pt-BR, já formatada) é o fallback exposto por {@link #getMessage()}.
     */
    public RegraDeNegocioException(String messageKey, String defaultMessage, Object... args) {
        super(defaultMessage);
        this.messageKey = messageKey;
        this.args = args;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArgs() {
        return args;
    }
}
