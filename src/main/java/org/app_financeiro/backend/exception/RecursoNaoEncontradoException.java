package org.app_financeiro.backend.exception;

/** Recurso não encontrado ou não pertence ao usuário — oculta existência (IDOR). → 404 NOT_FOUND. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
