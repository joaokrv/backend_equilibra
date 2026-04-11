package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
     * Soma o valor total de todas as receitas ativas de um usuário.
     */
    @Query("SELECT SUM(t.valor) FROM TransacaoEntity t WHERE t.usuario.id = :usuarioId AND t.tipo = org.app_financeiro.backend.enums.TipoTransacao.RECEITA")
    BigDecimal somarReceitasPorUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Soma o valor total de todas as despesas ativas de um usuário.
     */
    @Query("SELECT SUM(t.valor) FROM TransacaoEntity t WHERE t.usuario.id = :usuarioId AND t.tipo = org.app_financeiro.backend.enums.TipoTransacao.DESPESA")
    BigDecimal somarDespesasPorUsuario(@Param("usuarioId") Long usuarioId);

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

    /**
     * Verifica se já existe uma transação com a mesma chave de idempotência.
     */
    boolean existsByIdempotencyKey(String idempotencyKey);

        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE TransacaoEntity t
                        SET t.isAtivo = false
                        WHERE t.usuario.id = :usuarioId
                            AND t.conta IS NOT NULL
                            AND t.conta.id = :contaId
                        """)
        int inativarPorConta(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId);

                        @Modifying(clearAutomatically = true)
                        @Transactional
                        @Query("""
                            UPDATE TransacaoEntity t
                            SET t.isAtivo = false
                            WHERE t.usuario.id = :usuarioId
                              AND t.cartao IS NOT NULL
                              AND t.cartao.id = :cartaoId
                            """)
                        int inativarPorCartao(@Param("usuarioId") Long usuarioId, @Param("cartaoId") Long cartaoId);

                        @Modifying(clearAutomatically = true)
                        @Transactional
                        @Query("""
                            UPDATE TransacaoEntity t
                            SET t.categoria = null
                            WHERE t.usuario.id = :usuarioId
                              AND t.categoria IS NOT NULL
                              AND t.categoria.id = :categoriaId
                            """)
                        int desassociarCategoria(@Param("usuarioId") Long usuarioId, @Param("categoriaId") Long categoriaId);
}
