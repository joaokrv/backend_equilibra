package org.app_financeiro.backend.exception;

/**
 * Exceção lançada quando o usuário tenta realizar uma operação que não é permitida
 * no estado atual do recurso (ex: deletar categoria que possui transações vinculadas,
 * editar transação já conciliada, etc.).
 *
 * HTTP Status: 409 CONFLICT (tratado no GlobalExceptionHandler).
 */
public class OperacaoNaoPermitidaException extends RegraDeNegocioException {

    public OperacaoNaoPermitidaException(String mensagem) {
        super(mensagem);
    }
}
