package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.app_financeiro.backend.dto.projections.DividaCartaoProjection;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FaturaRepository extends JpaRepository<FaturaEntity, Long> {

    List<FaturaEntity> findByCartaoIdAndStatusNot(Long cartaoId, StatusFatura status);

    boolean existsByCartaoIdAndStatusNot(Long cartaoId, StatusFatura status);

    List<FaturaEntity> findByCartaoId(Long cartaoId);

    /** Lazy Creation — localiza fatura do mês/ano ou retorna vazio para criação. */
    Optional<FaturaEntity> findByCartaoIdAndMesAndAno(Long cartaoId, Integer mes, Integer ano);

    /** Query agregada evita N+1 ao calcular dívidas de todos os cartões do usuário. */
    @Query("SELECT new org.app_financeiro.backend.dto.projections.DividaCartaoProjection(f.cartao.id, SUM(f.valorTotal - f.valorPago)) " +
           "FROM FaturaEntity f WHERE f.cartao.usuario.id = :usuarioId AND f.status != :status GROUP BY f.cartao.id")
    List<DividaCartaoProjection> somarDividasPorCartoes(@Param("usuarioId") Long usuarioId, @Param("status") StatusFatura status);

    /** Acionado pelo scheduler diário (ShedLock) como rede de segurança para faturas vencidas. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE FaturaEntity f SET f.status = :statusAtrasada WHERE f.status IN (:statusAberta, :statusFechada) AND f.dataVencimento < :hoje")
    int marcarFaturasComoAtrasadas(
            @Param("hoje") LocalDate hoje,
            @Param("statusAtrasada") StatusFatura statusAtrasada,
            @Param("statusAberta") StatusFatura statusAberta,
            @Param("statusFechada") StatusFatura statusFechada
    );

            @Modifying(clearAutomatically = true)
            @Transactional
            @Query("""
                UPDATE FaturaEntity f
                SET f.ativo = false
                WHERE f.usuario.id = :usuarioId
                  AND f.cartao.id = :cartaoId
                """)
            int inativarPorCartao(@Param("usuarioId") Long usuarioId, @Param("cartaoId") Long cartaoId);
}
