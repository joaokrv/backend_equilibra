package org.app_financeiro.backend.exception;

/**
 * Exceção genérica para quebras de regra de negócio.
 * Qualquer validação inteligente que falhe nos Services deve lançar esta exceção
 * (ou uma filha dela, como SaldoInsuficienteException).
 *
 * HTTP Status: 400 BAD_REQUEST (tratado no GlobalExceptionHandler).
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
