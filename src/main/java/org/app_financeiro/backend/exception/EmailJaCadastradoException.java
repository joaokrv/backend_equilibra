package org.app_financeiro.backend.exception;

/**
 * Exceção lançada ao tentar cadastrar um usuário com um e-mail que já existe no sistema.
 *
 * HTTP Status: 409 CONFLICT (tratado no GlobalExceptionHandler).
 */
public class EmailJaCadastradoException extends RegraDeNegocioException {

    public EmailJaCadastradoException() {
        super("Este e-mail já está cadastrado em nosso sistema.");
    }
}
