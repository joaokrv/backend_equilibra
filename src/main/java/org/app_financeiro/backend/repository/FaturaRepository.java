package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FaturaRepository extends JpaRepository<FaturaEntity, Long> {

    List<FaturaEntity> findByCartaoIdAndStatusNot(Long cartaoId, StatusFatura status);

    boolean existsByCartaoIdAndStatusNot(Long cartaoId, StatusFatura status);
}
