package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CartaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartaoRepository extends JpaRepository<CartaoEntity, Long> {
    List<CartaoEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);
}
