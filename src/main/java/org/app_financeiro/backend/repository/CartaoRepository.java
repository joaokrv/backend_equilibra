package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CartaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para operações de persistência de CartaoEntity.
 *
 * NOTA: A entidade CartaoEntity possui @SQLRestriction("ativo = true"),
 * portanto todas as queries derivadas filtram automaticamente por ativo = true.
 */
@Repository
public interface CartaoRepository extends JpaRepository<CartaoEntity, Long> {

    /**
     * Retorna todos os cartões (ativos) de um usuário.
     * Filtro ativo = true aplicado automaticamente via @SQLRestriction.
     *
     * @param usuarioId ID do usuário
     * @return Lista de cartões ativos
     */
    List<CartaoEntity> findByUsuarioId(Long usuarioId);
}
