package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {
    Optional<UsuarioEntity> findByEmailAndAtivoTrue(String email);
    Optional<UsuarioEntity> findByEmail(String email);

    List<UsuarioEntity> findByIdAndAtivo(Long id, boolean ativo);
}
