package org.app_financeiro.backend.exception;

/**
 * Exceção lançada quando o usuário tenta fazer login sem ter verificado o e-mail.
 *
 * HTTP Status: 403 FORBIDDEN (tratado no GlobalExceptionHandler).
 */
public class EmailNaoVerificadoException extends RegraDeNegocioException {

    public EmailNaoVerificadoException() {
        super("Verifique seu e-mail antes de fazer login. Confira sua caixa de entrada.");
    }
}
