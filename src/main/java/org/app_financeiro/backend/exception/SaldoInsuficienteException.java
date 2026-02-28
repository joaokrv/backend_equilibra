package org.app_financeiro.backend.exception;

/**
 * Exceção lançada quando uma operação tenta deduzir mais fundos
 * do que o saldo disponível na conta.
 *
 * HTTP Status: 422 UNPROCESSABLE_ENTITY (tratado no GlobalExceptionHandler).
 */
public class SaldoInsuficienteException extends RegraDeNegocioException {

    public SaldoInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
