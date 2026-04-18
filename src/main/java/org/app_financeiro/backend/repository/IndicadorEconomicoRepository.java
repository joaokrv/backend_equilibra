package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.IndicadorEconomicoEntity;
import org.app_financeiro.backend.entity.IndicadorEconomicoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Repositório para indicadores econômicos (SELIC, CDI, IPCA, etc.) — tabela particionada. */
@Repository
public interface IndicadorEconomicoRepository extends JpaRepository<IndicadorEconomicoEntity, IndicadorEconomicoId> {

    Optional<IndicadorEconomicoEntity> findFirstByNomeOrderByDataAtualizacaoDesc(String nome);

    List<IndicadorEconomicoEntity> findAllByDataAtualizacao(LocalDate data);

    /** Subquery por nome garante o snapshot mais recente de cada indicador — alimenta a barra dinâmica. */
    @Query("SELECT i FROM IndicadorEconomicoEntity i WHERE i.dataAtualizacao = (SELECT MAX(i2.dataAtualizacao) FROM IndicadorEconomicoEntity i2 WHERE i2.nome = i.nome)")
    List<IndicadorEconomicoEntity> buscarUltimosIndicadores();

}
