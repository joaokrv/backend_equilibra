package org.app_financeiro.backend.exception;

/**
 * Exceção lançada quando as credenciais de login (e-mail/senha) estão incorretas.
 * A mensagem é genérica propositalmente para não revelar se o e-mail existe ou não.
 *
 * HTTP Status: 401 UNAUTHORIZED (tratado no GlobalExceptionHandler).
 */
public class CredenciaisInvalidasException extends RegraDeNegocioException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha incorretos.");
    }
}
