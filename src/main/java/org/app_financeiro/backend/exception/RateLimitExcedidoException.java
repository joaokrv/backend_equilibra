package org.app_financeiro.backend.exception;

/**
 * Lançada pelo RateLimitInterceptor quando o bucket do cliente está esgotado.
 * Carrega o escopo (auth, relatorio, health, geral) e segundos até reabastecer
 * — usados no GlobalExceptionHandler para montar a resposta 429 + Retry-After.
 */
public class RateLimitExcedidoException extends RuntimeException {

    private final String escopo;
    private final long retryAfterSeconds;

    public RateLimitExcedidoException(String escopo, long retryAfterSeconds) {
        super("Limite de requisições excedido para escopo: " + escopo);
        this.escopo = escopo;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getEscopo() {
        return escopo;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
