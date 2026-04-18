package org.app_financeiro.backend.exception;

/** E-mail duplicado. → 409 CONFLICT. */
public class EmailJaCadastradoException extends RegraDeNegocioException {

    public EmailJaCadastradoException() {
        super("Este e-mail já está cadastrado em nosso sistema.");
    }
}
