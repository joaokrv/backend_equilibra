package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContaRepository extends JpaRepository<ContaEntity, Long> {
    List<ContaEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);
}
