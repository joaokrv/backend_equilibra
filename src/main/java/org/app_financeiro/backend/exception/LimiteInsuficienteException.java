package org.app_financeiro.backend.exception;

/**
 * Exceção lançada quando uma despesa no cartão de crédito excede
 * o limite disponível do cartão.
 *
 * HTTP Status: 422 UNPROCESSABLE_ENTITY (tratado no GlobalExceptionHandler).
 */
public class LimiteInsuficienteException extends RegraDeNegocioException {

    public LimiteInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
