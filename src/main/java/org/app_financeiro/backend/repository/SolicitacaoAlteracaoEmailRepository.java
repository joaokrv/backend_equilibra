package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.SolicitacaoAlteracaoEmailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SolicitacaoAlteracaoEmailRepository extends JpaRepository<SolicitacaoAlteracaoEmailEntity, Long> {

    Optional<SolicitacaoAlteracaoEmailEntity> findByUsuarioIdAndCodigoAndIsUtilizadoFalse(Long usuarioId, String codigo);

    /** Busca independente do código — permite incrementar tentativas_falhas mesmo com código errado (lockout). */
    Optional<SolicitacaoAlteracaoEmailEntity> findTopByUsuarioIdAndIsUtilizadoFalseOrderByDataCriacaoDesc(Long usuarioId);

    @Modifying
    @Query("UPDATE SolicitacaoAlteracaoEmailEntity s SET s.isUtilizado = true WHERE s.usuarioId = :usuarioId AND s.isUtilizado = false")
    void invalidarSolicitacoesAnteriores(@Param("usuarioId") Long usuarioId);
}
