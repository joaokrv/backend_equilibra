package org.app_financeiro.backend.util;

import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;

/** Validações reutilizadas em múltiplos services. */
public final class ValidacaoRecursoUtil {

    private ValidacaoRecursoUtil() {}

    /**
     * Garante que uma transação pertença a conta OU cartão, nunca ambos nem nenhum.
     * Lança RegraDeNegocioException se a combinação for inválida.
     */
    public static void validarContaXorCartao(Long contaId, Long cartaoId) {
        if (contaId != null && cartaoId != null) {
            throw new RegraDeNegocioException(
                "error.transacao.conta_e_cartao",
                "Transação inválida: Uma transação não pode pertencer a uma conta bancária e a um cartão de crédito ao mesmo tempo."
            );
        }
        if (contaId == null && cartaoId == null) {
            throw new RegraDeNegocioException(
                "error.transacao.conta_ou_cartao_obrigatorio",
                "Transação inválida: É obrigatório vincular a transação a uma conta bancária ou a um cartão de crédito."
            );
        }
    }

    /**
     * Garante que o recurso pertence ao usuário autenticado.
     * Lança RecursoNaoEncontradoException (404) se não pertencer — evita vazamento de informação.
     */
    public static void validarPropriedade(Long usuarioIdRecurso, Long usuarioIdRequisicao, String nomeRecurso) {
        if (!usuarioIdRecurso.equals(usuarioIdRequisicao)) {
            throw new RecursoNaoEncontradoException(nomeRecurso + " não encontrado");
        }
    }
}
