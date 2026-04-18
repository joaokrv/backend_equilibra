package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** @SQLRestriction("ativo = true") filtra automaticamente em todas as queries derivadas. */
@Repository
public interface InvestimentoRepository extends JpaRepository<InvestimentoEntity, Long> {

    @Query("SELECT SUM(i.valorAtual) FROM InvestimentoEntity i WHERE i.usuario.id = :usuarioId")
    BigDecimal somarTotalInvestidoPorUsuario(@Param("usuarioId") Long usuarioId);

    /** @EntityGraph evita N+1 ao carregar contaOrigem e contaDestino. */
    @EntityGraph(attributePaths = {"contaOrigem", "contaDestino"})
    List<InvestimentoEntity> findByUsuarioId(Long usuarioId);

        /** Protege exclusão de conta — bloqueia se houver vínculos ativos como origem ou destino. */
        @Query("""
                        SELECT COUNT(i) FROM InvestimentoEntity i
                        WHERE i.usuario.id = :usuarioId
                            AND (
                                     (i.contaOrigem IS NOT NULL AND i.contaOrigem.id = :contaId)
                                OR (i.contaDestino IS NOT NULL AND i.contaDestino.id = :contaId)
                            )
                        """)
        long countAtivosVinculadosAConta(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId);

        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE InvestimentoEntity i
                        SET i.ativo = false
                        WHERE i.usuario.id = :usuarioId
                            AND (
                                     (i.contaOrigem IS NOT NULL AND i.contaOrigem.id = :contaId)
                                OR (i.contaDestino IS NOT NULL AND i.contaDestino.id = :contaId)
                            )
                        """)
        int inativarVinculadosAConta(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId);
}
