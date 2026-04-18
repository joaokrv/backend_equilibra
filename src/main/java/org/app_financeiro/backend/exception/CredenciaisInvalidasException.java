package org.app_financeiro.backend.exception;

/** Credenciais incorretas. Mensagem genérica — anti-enumeração (B1-A2). → 401 UNAUTHORIZED. */
public class CredenciaisInvalidasException extends RegraDeNegocioException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha incorretos.");
    }
}
