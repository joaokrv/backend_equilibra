package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.enums.StatusNotificacaoFatura;
import org.app_financeiro.backend.enums.TipoLembreteFatura;
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

    /** Faturas com vencimento na data exata, status elegível, opt-in ativo e email verificado. */
    @Query("""
        SELECT f FROM FaturaEntity f
        JOIN FETCH f.cartao c
        JOIN FETCH f.usuario u
        WHERE f.dataVencimento = :dataVencimento
          AND f.status IN :statusElegiveis
          AND u.notificacoesFaturaAtivo = true
          AND u.isEmailVerificado = true
        """)
    List<FaturaEntity> findElegiveisParaLembrete(
            @Param("dataVencimento") LocalDate dataVencimento,
            @Param("statusElegiveis") List<StatusFatura> statusElegiveis);

    /** Faturas ATRASADAS cujo usuário ainda não recebeu notificação de ATRASO com status ENVIADO. */
    @Query("""
        SELECT f FROM FaturaEntity f
        JOIN FETCH f.cartao c
        JOIN FETCH f.usuario u
        WHERE f.status = :statusAtrasada
          AND u.notificacoesFaturaAtivo = true
          AND u.isEmailVerificado = true
          AND NOT EXISTS (
              SELECT 1 FROM NotificacaoFaturaEntity n
              WHERE n.faturaId = f.id
                AND n.tipo = :tipoAtraso
                AND n.status = :statusEnviado
          )
        """)
    List<FaturaEntity> findAtrasadasSemNotificacao(
            @Param("statusAtrasada") StatusFatura statusAtrasada,
            @Param("tipoAtraso") TipoLembreteFatura tipoAtraso,
            @Param("statusEnviado") StatusNotificacaoFatura statusEnviado);
}
