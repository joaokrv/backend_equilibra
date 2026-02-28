package org.app_financeiro.backend.service;

import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class FaturaService {

    /**
     * Adiciona o valor de uma transação à fatura correspondente.
     * Se a fatura não existir para o mês/ano da transação, cria uma nova.
     * Atualiza o valorTotal da fatura e salva.
     *
     * @param cartao Cartão associado à transação
     * @param dataTransacao Data em que a transação ocorreu
     * @param valor Valor da transação a ser adicionado
     * @return A FaturaEntity atualizada/criada
     */
    @Transactional
    public FaturaEntity adicionarTransacao(CartaoEntity cartao, LocalDate dataTransacao, BigDecimal valor) {
        // TODO: Implementar - buscar ou criar fatura, adicionar valor, salvar
        return null;
    }

    /**
     * Remove o valor de uma transação da fatura correspondente.
     * Usado quando uma transação é deletada ou tem seu valor atualizado (estorno do valor antigo).
     * Subtrai o valor do valorTotal da fatura e salva.
     *
     * @param cartao Cartão associado à transação
     * @param dataTransacao Data da transação original
     * @param valor Valor a ser subtraído
     */
    @Transactional
    public void removerTransacao(CartaoEntity cartao, LocalDate dataTransacao, BigDecimal valor) {
        // TODO: Implementar - buscar fatura, subtrair valor, salvar
    }

    /**
     * Paga uma fatura existente, alterando seu status para PAGA.
     * Valida se a fatura pertence ao usuário.
     *
     * @param faturaId ID da fatura
     * @param usuarioId ID do usuário
     * @return Entidade da fatura atualizada
     */
    @Transactional
    public Object pagarFatura(Long faturaId, Long usuarioId) {
        // TODO: Implementar - buscar fatura validando usuário, alterar status para PAGA, salvar
        return null;
    }

    /**
     * Lista todas as faturas de um cartão específico do usuário.
     *
     * @param cartaoId ID do cartão
     * @param usuarioId ID do usuário
     * @return Lista de faturas do cartão
     */
    public List<Object> listarFaturasPorCartao(Long cartaoId, Long usuarioId) {
        // TODO: Implementar - validar cartão do usuário, buscar faturas, retornar
        return List.of();
    }
}
