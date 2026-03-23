package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositório JPA para operações de persistência de TransacaoEntity.
 * Oferece queries por usuário, período, tipo, categoria, conta e cartão,
 * além de suporte a paginação.
 *
 * NOTA: A entidade TransacaoEntity possui @SQLRestriction("ativo = true"),
 * portanto todas as queries derivadas filtram automaticamente por ativo = true.
 */
@Repository
public interface TransacaoRepository extends JpaRepository<TransacaoEntity, Long> {

    /**
     * Retorna todas as transações (ativas) de um usuário.
     */
    List<TransacaoEntity> findByUsuarioId(Long usuarioId);

    /**
     * Retorna transações (ativas) de um usuário em um intervalo de datas.
     */
    @EntityGraph(attributePaths = {"categoria", "conta", "cartao"})
    List<TransacaoEntity> findByUsuarioIdAndDataBetween(Long usuarioId, LocalDate start, LocalDate end);

    /**
     * Retorna transações (ativas) de um usuário filtradas por tipo (RECEITA ou DESPESA).
     */
    List<TransacaoEntity> findByUsuarioIdAndTipo(Long usuarioId, TipoTransacao tipo);

    /**
     * Retorna transações (ativas) de um usuário em um período, filtradas por categoria.
     */
    List<TransacaoEntity> findByUsuarioIdAndCategoriaAndDataBetween(Long usuarioId, CategoriaEntity categoria, LocalDate start, LocalDate end);

    /**
     * Retorna transações (ativas) de um usuário em um período, filtradas por conta bancária.
     */
    List<TransacaoEntity> findByUsuarioIdAndContaAndDataBetween(Long usuarioId, ContaEntity conta, LocalDate start, LocalDate end);

    /**
     * Retorna transações (ativas) de um usuário em um período, filtradas por cartão de crédito.
     */
    List<TransacaoEntity> findByUsuarioIdAndCartaoAndDataBetween(Long usuarioId, CartaoEntity cartao, LocalDate start, LocalDate end);

    /**
     * Retorna transações (ativas) de um usuário com suporte a paginação.
     */
    Page<TransacaoEntity> findByUsuarioId(Long usuarioId, Pageable pageable);
}
