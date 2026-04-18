package org.app_financeiro.backend.exception;

/** Operação inválida no estado atual do recurso (ex: idempotency, categoria com transações). → 409 CONFLICT. */
public class OperacaoNaoPermitidaException extends RegraDeNegocioException {

    public OperacaoNaoPermitidaException(String mensagem) {
        super(mensagem);
    }
}
