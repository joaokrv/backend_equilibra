package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.PatrimonioHistoricoEntity;
import org.app_financeiro.backend.entity.PatrimonioHistoricoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatrimonioHistoricoRepository extends JpaRepository<PatrimonioHistoricoEntity, PatrimonioHistoricoId> {

    /**
     * Busca o histórico de patrimônio de um usuário em um intervalo de datas para o gráfico de evolução.
     */
    List<PatrimonioHistoricoEntity> findByUsuarioIdAndDataReferenciaBetweenOrderByDataReferenciaAsc(
            Long usuarioId, LocalDate start, LocalDate end);

    /**
     * Verifica se já existe um snapshot para o usuário na data de referência para evitar duplicidade.
     */
    Optional<PatrimonioHistoricoEntity> findByUsuarioIdAndDataReferencia(Long usuarioId, LocalDate dataReferencia);

    /**
     * Obtém o próximo ID da sequência do BIGSERIAL para compatibilizar com PK composta (id, data_referencia).
     */
    @Query(value = "SELECT nextval(pg_get_serial_sequence('patrimonio_historico', 'id'))", nativeQuery = true)
    Long nextId();
}
