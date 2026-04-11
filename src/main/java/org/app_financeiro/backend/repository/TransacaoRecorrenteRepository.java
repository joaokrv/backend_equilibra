package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.TransacaoRecorrenteEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransacaoRecorrenteRepository extends JpaRepository<TransacaoRecorrenteEntity, Long> {

    @EntityGraph(attributePaths = {"conta", "cartao", "categoria"})
    List<TransacaoRecorrenteEntity> findByUsuarioId(Long usuarioId);

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
}
