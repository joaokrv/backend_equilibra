package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.StatusTransacao;
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

/** @SQLRestriction("ativo = true") filtra automaticamente em todas as queries derivadas. */
@Repository
public interface TransacaoRepository extends JpaRepository<TransacaoEntity, Long> {

    /** Filtra por PAGO para não inflar o resumo com transações pendentes. */
    @Query("SELECT SUM(t.valor) FROM TransacaoEntity t WHERE t.usuario.id = :usuarioId AND t.tipo = org.app_financeiro.backend.enums.TipoTransacao.RECEITA AND t.status = org.app_financeiro.backend.enums.StatusTransacao.PAGO")
    BigDecimal somarReceitasPorUsuario(@Param("usuarioId") Long usuarioId);

    /** Filtra por PAGO para não inflar o resumo com transações pendentes. */
    @Query("SELECT SUM(t.valor) FROM TransacaoEntity t WHERE t.usuario.id = :usuarioId AND t.tipo = org.app_financeiro.backend.enums.TipoTransacao.DESPESA AND t.status = org.app_financeiro.backend.enums.StatusTransacao.PAGO")
    BigDecimal somarDespesasPorUsuario(@Param("usuarioId") Long usuarioId);

    List<TransacaoEntity> findByUsuarioId(Long usuarioId);

    /** @EntityGraph evita N+1 ao carregar categoria, conta e cartão. */
    @EntityGraph(attributePaths = {"categoria", "conta", "cartao"})
    List<TransacaoEntity> findByUsuarioIdAndDataBetween(Long usuarioId, LocalDate start, LocalDate end);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransacaoEntity t " +
           "WHERE t.usuario.id = :uid AND t.data BETWEEN :ini AND :fim AND t.tipo = :tipo")
    BigDecimal somarPorTipoNoPeriodo(@Param("uid") Long uid,
                                      @Param("ini") LocalDate ini,
                                      @Param("fim") LocalDate fim,
                                      @Param("tipo") TipoTransacao tipo);

    @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransacaoEntity t " +
           "WHERE t.usuario.id = :uid AND t.data BETWEEN :ini AND :fim " +
           "AND t.tipo = :tipo AND t.status = :status")
    BigDecimal somarPorTipoEStatusNoPeriodo(@Param("uid") Long uid,
                                             @Param("ini") LocalDate ini,
                                             @Param("fim") LocalDate fim,
                                             @Param("tipo") TipoTransacao tipo,
                                             @Param("status") StatusTransacao status);

    Page<TransacaoEntity> findByUsuarioId(Long usuarioId, Pageable pageable);

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

    @EntityGraph(attributePaths = {"categoria", "conta", "cartao"})
    @Query("SELECT t FROM TransacaoEntity t WHERE t.usuario.id = :usuarioId " +
            "AND t.data BETWEEN :start AND :end " +
            "AND (:tipo IS NULL OR t.tipo = :tipo) " +
            "AND (:status IS NULL OR t.status = :status)")
    List<TransacaoEntity> buscarParaRelatorio(
            @Param("usuarioId") Long usuarioId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("tipo") TipoTransacao tipo,
            @Param("status") StatusTransacao status);
}
