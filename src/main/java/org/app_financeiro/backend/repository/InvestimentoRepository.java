package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para operações de persistência de InvestimentoEntity.
 */
@Repository
public interface InvestimentoRepository extends JpaRepository<InvestimentoEntity, Long> {

    /**
     * Retorna todos os investimentos ativos de um usuário.
     *
     * @param usuarioId ID do usuário
     * @return Lista de investimentos ativos
     */
    List<InvestimentoEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);
}
