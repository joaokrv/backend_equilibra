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

/**
 * Repositório JPA para operações de persistência de InvestimentoEntity.
 *
 * NOTA: A entidade InvestimentoEntity possui @SQLRestriction("ativo = true"),
 * portanto todas as queries derivadas filtram automaticamente por ativo = true.
 */
@Repository
public interface InvestimentoRepository extends JpaRepository<InvestimentoEntity, Long> {

    /**
     * Soma o valor total acumulado de todos os investimentos ativos de um usuário.
     */
    @Query("SELECT SUM(i.valorAtual) FROM InvestimentoEntity i WHERE i.usuario.id = :usuarioId")
    BigDecimal somarTotalInvestidoPorUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Retorna todos os investimentos (ativos) de um usuário.
     * Filtro ativo = true aplicado automaticamente via @SQLRestriction.
     *
     * @param usuarioId ID do usuário
     * @return Lista de investimentos ativos
     */
    @EntityGraph(attributePaths = {"contaOrigem", "contaDestino"})
    List<InvestimentoEntity> findByUsuarioId(Long usuarioId);

        /**
         * Conta quantos investimentos ativos do usuário ainda referenciam uma conta
         * como origem ou destino. Usado para proteger a exclusão de contas e evitar
         * vínculos órfãos no patrimônio.
         */
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
