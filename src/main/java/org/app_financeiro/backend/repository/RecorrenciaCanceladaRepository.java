package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.RecorrenciaCanceladaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecorrenciaCanceladaRepository extends JpaRepository<RecorrenciaCanceladaEntity, Long> {

    boolean existsByRecorrenteIdAndAnoAndMes(Long recorrenteId, Integer ano, Integer mes);

    void deleteByRecorrenteIdAndAnoAndMes(Long recorrenteId, Integer ano, Integer mes);
}
