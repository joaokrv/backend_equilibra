package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.PatrimonioHistoricoEntity;
import org.app_financeiro.backend.entity.PatrimonioHistoricoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatrimonioHistoricoRepository extends JpaRepository<PatrimonioHistoricoEntity, PatrimonioHistoricoId> {

    List<PatrimonioHistoricoEntity> findByUsuarioIdAndDataReferenciaBetweenOrderByDataReferenciaAsc(
            Long usuarioId, LocalDate start, LocalDate end);

    Optional<PatrimonioHistoricoEntity> findByUsuarioIdAndDataReferencia(Long usuarioId, LocalDate dataReferencia);

    /** Query nativa — obtém próximo valor da sequência. pg_get_serial_sequence é unreliable em tabelas particionadas. */
    @Query(value = "SELECT nextval('patrimonio_historico_id_seq')", nativeQuery = true)
    Long nextId();

    /** Usado para calcular variação de saldo/investimentos por período. */
    @Query("SELECT p FROM PatrimonioHistoricoEntity p " +
           "WHERE p.usuario.id = :uid AND p.dataReferencia BETWEEN :ini AND :fim " +
           "ORDER BY p.dataReferencia DESC LIMIT 1")
    Optional<PatrimonioHistoricoEntity> findMaisRecentePorUsuarioNoIntervalo(
            @Param("uid") Long uid,
            @Param("ini") LocalDate ini,
            @Param("fim") LocalDate fim);
}
