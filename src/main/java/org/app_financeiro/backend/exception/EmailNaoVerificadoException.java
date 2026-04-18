package org.app_financeiro.backend.exception;

/** Login bloqueado — e-mail não verificado. → 403 FORBIDDEN. */
public class EmailNaoVerificadoException extends RegraDeNegocioException {

    public EmailNaoVerificadoException() {
        super("Verifique seu e-mail antes de fazer login. Confira sua caixa de entrada.");
    }
}
