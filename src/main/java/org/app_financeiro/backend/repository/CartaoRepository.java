package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CartaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para operações de persistência de CartaoEntity.
 */
@Repository
public interface CartaoRepository extends JpaRepository<CartaoEntity, Long> {

    /**
     * Retorna todos os cartões ativos de um usuário.
     *
     * @param usuarioId ID do usuário
     * @return Lista de cartões ativos
     */
    List<CartaoEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);
}
