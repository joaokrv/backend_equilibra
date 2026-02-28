package org.app_financeiro.backend.exception;

/**
 * Exceção lançada quando um recurso (Entidade) não é encontrado no banco de dados.
 * Também usada quando o recurso existe, mas não pertence ao usuário autenticado
 * (para não revelar a existência do recurso a outro usuário).
 *
 * HTTP Status: 404 NOT_FOUND (tratado no GlobalExceptionHandler).
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
