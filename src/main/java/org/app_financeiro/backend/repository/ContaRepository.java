package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para operações de persistência de ContaEntity.
 */
@Repository
public interface ContaRepository extends JpaRepository<ContaEntity, Long> {

    /**
     * Retorna todas as contas ativas de um usuário.
     *
     * @param usuarioId ID do usuário
     * @return Lista de contas ativas
     */
    List<ContaEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);
}
