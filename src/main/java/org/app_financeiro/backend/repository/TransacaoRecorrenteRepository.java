package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.TransacaoRecorrenteEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** @SQLRestriction("ativo = true") filtra automaticamente em todas as queries derivadas. */
@Repository
public interface TransacaoRecorrenteRepository extends JpaRepository<TransacaoRecorrenteEntity, Long> {

    /** @EntityGraph evita N+1 ao carregar conta, cartão e categoria. */
    @EntityGraph(attributePaths = {"conta", "cartao", "categoria"})
    List<TransacaoRecorrenteEntity> findByUsuarioId(Long usuarioId);

    /** Filtra por dono — previne IDOR ao vincular recorrência a uma transação. */
    Optional<TransacaoRecorrenteEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Query("""
        SELECT r FROM TransacaoRecorrenteEntity r
        WHERE r.diaLancamento = :dia
          AND r.dataInicio <= :dataAtual
          AND (r.dataFim IS NULL OR r.dataFim >= :dataAtual)
    """)
    List<TransacaoRecorrenteEntity> findAtivasParaProcessar(
        @Param("dia") int dia,
        @Param("dataAtual") LocalDate dataAtual
    );

        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE TransacaoRecorrenteEntity r
                        SET r.ativo = false
                        WHERE r.usuario.id = :usuarioId
                            AND r.conta.id = :contaId
                        """)
        int inativarPorConta(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId);

                        @Modifying(clearAutomatically = true)
                        @Transactional
                        @Query("""
                            UPDATE TransacaoRecorrenteEntity r
                            SET r.ativo = false
                            WHERE r.usuario.id = :usuarioId
                              AND r.cartao IS NOT NULL
                              AND r.cartao.id = :cartaoId
                            """)
                        int inativarPorCartao(@Param("usuarioId") Long usuarioId, @Param("cartaoId") Long cartaoId);

                        @Modifying(clearAutomatically = true)
                        @Transactional
                        @Query("""
                            UPDATE TransacaoRecorrenteEntity r
                            SET r.categoria = null
                            WHERE r.usuario.id = :usuarioId
                              AND r.categoria IS NOT NULL
                              AND r.categoria.id = :categoriaId
                            """)
                        int desassociarCategoria(@Param("usuarioId") Long usuarioId, @Param("categoriaId") Long categoriaId);
}
