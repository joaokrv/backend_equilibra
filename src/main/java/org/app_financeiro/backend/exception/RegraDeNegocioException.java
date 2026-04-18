package org.app_financeiro.backend.exception;

/** Base para violações de regra de negócio nos services. → 400 BAD_REQUEST (subclasses podem sobrescrever). */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
