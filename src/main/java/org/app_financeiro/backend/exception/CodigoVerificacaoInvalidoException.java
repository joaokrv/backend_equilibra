package org.app_financeiro.backend.exception;

/**
 * Exceção lançada quando o código de verificação de e-mail é inválido,
 * já foi utilizado ou expirou.
 *
 * HTTP Status: 400 BAD_REQUEST (tratado no GlobalExceptionHandler).
 */
public class CodigoVerificacaoInvalidoException extends RegraDeNegocioException {

    public CodigoVerificacaoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
