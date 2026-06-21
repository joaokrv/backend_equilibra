package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.UsuarioFotoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioFotoRepository extends JpaRepository<UsuarioFotoEntity, Long> {
}
