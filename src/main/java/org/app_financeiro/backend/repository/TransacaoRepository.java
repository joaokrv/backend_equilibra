package org.app_financeiro.backend.repository;

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
import java.util.UUID;

/** @SQLRestriction("ativo = true") filtra automaticamente em todas as queries derivadas. */
@Repository
public interface TransacaoRepository extends JpaRepository<TransacaoEntity, Long> {

    /** Filtra por PAGO para não inflar o resumo com transações pendentes. */
    @Query("SELECT SUM(t.valor) FROM TransacaoEntity t WHERE t.usuario.id = :usuarioId AND t.tipo = TipoTransacao.RECEITA AND t.status = StatusTransacao.PAGO AND t.transferencia = false")
    BigDecimal somarReceitasPorUsuario(@Param("usuarioId") Long usuarioId);

    /** Filtra por PAGO para não inflar o resumo com transações pendentes. */
    @Query("SELECT SUM(t.valor) FROM TransacaoEntity t WHERE t.usuario.id = :usuarioId AND t.tipo = TipoTransacao.DESPESA AND t.status = StatusTransacao.PAGO AND t.transferencia = false")
    BigDecimal somarDespesasPorUsuario(@Param("usuarioId") Long usuarioId);

    List<TransacaoEntity> findByUsuarioId(Long usuarioId);

          /** @EntityGraph evita N+1 ao carregar categoria, conta, cartão e fatura. */
          @EntityGraph(attributePaths = {"categoria", "conta", "cartao", "fatura"})
          @Query("SELECT t FROM TransacaoEntity t " +
            "LEFT JOIN t.fatura f " +
            "WHERE t.usuario.id = :usuarioId AND (" +
            "(f IS NOT NULL AND f.dataFechamento BETWEEN :start AND :end) OR " +
            "(f IS NULL AND t.data BETWEEN :start AND :end))")
          List<TransacaoEntity> findByUsuarioIdAndDataBetween(Long usuarioId, LocalDate start, LocalDate end);

          @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransacaoEntity t " +
            "LEFT JOIN t.fatura f " +
            "WHERE t.usuario.id = :uid AND t.tipo = :tipo AND t.transferencia = false AND t.isAtivo = true AND (" +
            "(f IS NOT NULL AND f.dataFechamento BETWEEN :ini AND :fim) OR " +
            "(f IS NULL AND t.data BETWEEN :ini AND :fim))")
    BigDecimal somarPorTipoNoPeriodo(@Param("uid") Long uid,
                                      @Param("ini") LocalDate ini,
                                      @Param("fim") LocalDate fim,
                                      @Param("tipo") TipoTransacao tipo);

          @Query("SELECT COALESCE(SUM(t.valor), 0) FROM TransacaoEntity t " +
             "LEFT JOIN t.fatura f " +
             "WHERE t.usuario.id = :uid AND t.tipo = :tipo AND t.status = :status AND t.transferencia = false AND t.isAtivo = true AND (" +
             "(f IS NOT NULL AND f.dataFechamento BETWEEN :ini AND :fim) OR " +
             "(f IS NULL AND t.data BETWEEN :ini AND :fim))")
    BigDecimal somarPorTipoEStatusNoPeriodo(@Param("uid") Long uid,
                                             @Param("ini") LocalDate ini,
                                             @Param("fim") LocalDate fim,
                                             @Param("tipo") TipoTransacao tipo,
                                             @Param("status") StatusTransacao status);

    Page<TransacaoEntity> findByUsuarioId(Long usuarioId, Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"categoria", "conta", "cartao"})
    List<TransacaoEntity> findByFaturaId(Long faturaId);

    /** Parcelas ativas de uma mesma compra parcelada. @SQLRestriction já filtra ativo = true. */
    @EntityGraph(attributePaths = {"cartao", "fatura"})
    List<TransacaoEntity> findByGrupoParcelamentoAndUsuarioId(UUID grupoParcelamento, Long usuarioId);

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
        @Query("SELECT t FROM TransacaoEntity t " +
          "LEFT JOIN t.fatura f " +
          "WHERE t.usuario.id = :usuarioId AND (" +
          "(f IS NOT NULL AND f.dataFechamento BETWEEN :start AND :end) OR " +
          "(f IS NULL AND t.data BETWEEN :start AND :end)) " +
          "AND (:tipo IS NULL OR t.tipo = :tipo) " +
          "AND (:status IS NULL OR t.status = :status)")
    List<TransacaoEntity> buscarParaRelatorio(
            @Param("usuarioId") Long usuarioId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("tipo") TipoTransacao tipo,
            @Param("status") StatusTransacao status);

    long countByFaturaId(Long faturaId);

    /**
     * Detecção de duplicata em lote via pg_trgm: mesma data, valor e descrição similar (>70%).
     * Recebe as candidatas como JSON (array de {indice, data, valor, descricao}) e devolve os
     * índices que colidem com transações existentes — 1 round-trip para o lote inteiro, em vez
     * de 1 query por candidata (N+1 de até 500 queries por upload).
     */
    @Query(value = """
            SELECT c.indice
            FROM jsonb_to_recordset(cast(:candidatasJson as jsonb))
                 AS c(indice int, data date, valor numeric, descricao text)
            WHERE EXISTS (
                SELECT 1 FROM transacoes t
                WHERE t.usuario_id = :usuarioId
                  AND t.ativo = true
                  AND t.data = c.data
                  AND ABS(t.valor - c.valor) < 0.01
                  AND similarity(t.descricao, c.descricao) > 0.7
            )
            """, nativeQuery = true)
    List<Integer> buscarIndicesDuplicados(
            @Param("usuarioId") Long usuarioId,
            @Param("candidatasJson") String candidatasJson);

    /** Exclui fisicamente (soft delete via ativo=false) todas as transações de uma importação.
     *  Retorna os IDs para que o caller desfaça os impactos financeiros individualmente. */
    @Query("SELECT t FROM TransacaoEntity t WHERE t.importacaoId = :importacaoId AND t.usuario.id = :usuarioId")
    List<TransacaoEntity> findByImportacaoIdAndUsuarioId(
            @Param("importacaoId") java.util.UUID importacaoId,
            @Param("usuarioId") Long usuarioId);
}
