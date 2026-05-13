package org.app_financeiro.backend.util;

import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;

import java.util.Optional;

/**
 * Classe utilitária para validações comuns no sistema.
 * Reduz boilerplate de busca + orElseThrow nos Services.
 */
public final class ValidacaoUtils {

    private ValidacaoUtils() {
    }

    /**
     * Extrai o valor de um Optional ou lança RecursoNaoEncontradoException.
     *
     * @param optional Optional com o resultado da busca
     * @param mensagem Mensagem da exceção caso não encontrado
     * @param <T>      Tipo do recurso
     * @return O recurso encontrado
     * @throws RecursoNaoEncontradoException se o Optional estiver vazio
     */
    public static <T> T buscarOuFalhar(Optional<T> optional, String mensagem) {
        return optional.orElseThrow(() -> new RecursoNaoEncontradoException(mensagem));
    }
}
