package org.app_financeiro.backend.exception;

/** OTP inválido, expirado ou já usado. → 400 BAD_REQUEST. */
public class CodigoVerificacaoInvalidoException extends RegraDeNegocioException {

    public CodigoVerificacaoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
