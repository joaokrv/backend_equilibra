package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para operações de persistência de ContaEntity.
 *
 * NOTA: A entidade ContaEntity possui @SQLRestriction("ativo = true"),
 * portanto todas as queries derivadas filtram automaticamente por ativo = true.
 */
@Repository
public interface ContaRepository extends JpaRepository<ContaEntity, Long> {

    /**
     * Retorna todas as contas (ativas) de um usuário.
     * Filtro ativo = true aplicado automaticamente via @SQLRestriction.
     *
     * @param usuarioId ID do usuário
     * @return Lista de contas ativas
     */
    List<ContaEntity> findByUsuarioId(Long usuarioId);
}
