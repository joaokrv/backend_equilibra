package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvestimentoRepository extends JpaRepository<InvestimentoEntity, Long> {
    List<InvestimentoEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);
}
