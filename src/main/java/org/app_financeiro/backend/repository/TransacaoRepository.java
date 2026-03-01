package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositório JPA para operações de persistência de TransacaoEntity.
 * Oferece queries por usuário, período, tipo, categoria, conta e cartão,
 * além de suporte a paginação.
 */
@Repository
public interface TransacaoRepository extends JpaRepository<TransacaoEntity, Long> {

    /**
     * Retorna todas as transações ativas de um usuário.
     *
     * @param usuarioId ID do usuário
     * @return Lista de transações ativas
     */
    List<TransacaoEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    /**
     * Retorna transações ativas de um usuário em um intervalo de datas.
     *
     * @param usuarioId ID do usuário
     * @param start     Data de início do intervalo (inclusive)
     * @param end       Data de fim do intervalo (inclusive)
     * @return Lista de transações no período
     */
    List<TransacaoEntity> findByUsuarioIdAndDataBetweenAndAtivoTrue(Long usuarioId, LocalDate start, LocalDate end);

    /**
     * Retorna transações ativas de um usuário filtradas por tipo (RECEITA ou DESPESA).
     *
     * @param usuarioId ID do usuário
     * @param tipo      Tipo da transação
     * @return Lista de transações do tipo informado
     */
    List<TransacaoEntity> findByUsuarioIdAndTipoAndAtivoTrue(Long usuarioId, TipoTransacao tipo);

    /**
     * Retorna transações ativas de um usuário em um período, filtradas por categoria.
     *
     * @param usuarioId  ID do usuário
     * @param categoria  Entidade da categoria
     * @param start      Data de início do intervalo
     * @param end        Data de fim do intervalo
     * @return Lista de transações da categoria no período
     */
    List<TransacaoEntity> findByUsuarioIdAndCategoriaAndDataBetweenAndAtivoTrue(Long usuarioId, CategoriaEntity categoria, LocalDate start, LocalDate end);

    /**
     * Retorna transações ativas de um usuário em um período, filtradas por conta bancária.
     *
     * @param usuarioId ID do usuário
     * @param conta     Entidade da conta
     * @param start     Data de início do intervalo
     * @param end       Data de fim do intervalo
     * @return Lista de transações da conta no período
     */
    List<TransacaoEntity> findByUsuarioIdAndContaAndDataBetweenAndAtivoTrue(Long usuarioId, ContaEntity conta, LocalDate start, LocalDate end);

    /**
     * Retorna transações ativas de um usuário em um período, filtradas por cartão de crédito.
     *
     * @param usuarioId ID do usuário
     * @param cartao    Entidade do cartão
     * @param start     Data de início do intervalo
     * @param end       Data de fim do intervalo
     * @return Lista de transações do cartão no período
     */
    List<TransacaoEntity> findByUsuarioIdAndCartaoAndDataBetweenAndAtivoTrue(Long usuarioId, CartaoEntity cartao, LocalDate start, LocalDate end);

    /**
     * Retorna transações ativas de um usuário com suporte a paginação.
     *
     * @param usuarioId ID do usuário
     * @param pageable  Configuração de paginação e ordenação
     * @return Página de transações ativas
     */
    Page<TransacaoEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId, Pageable pageable);
}
