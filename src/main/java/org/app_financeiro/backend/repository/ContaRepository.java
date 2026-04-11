package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para operações de persistência de ContaEntity.
 *
 * NOTA: A entidade ContaEntity possui @SQLRestriction("ativo = true"),
 * portanto todas as queries derivadas filtram automaticamente por ativo = true.
 */
@Repository
public interface ContaRepository extends JpaRepository<ContaEntity, Long> {

    /**
     * Soma o saldo de todas as contas ativas de um usuário.
     */
    @Query("SELECT SUM(c.saldo) FROM ContaEntity c WHERE c.usuario.id = :usuarioId")
    BigDecimal somarSaldoPorUsuario(@Param("usuarioId") Long usuarioId);

    /**
     * Retorna todas as contas (ativas) de um usuário.
     * Filtro ativo = true aplicado automaticamente via @SQLRestriction.
     *
     * @param usuarioId ID do usuário
     * @return Lista de contas ativas
     */
    List<ContaEntity> findByUsuarioId(Long usuarioId);

    /**
     * Busca uma conta específica com bloqueio pessimista.
     * Garante que nenhuma outra thread possa ler ou modificar a conta até
     * que a transação atual seja concluída.
     *
     * @param id ID da conta
     * @return Optional contendo a conta com lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContaEntity c WHERE c.id = :id")
    Optional<ContaEntity> findByIdWithLock(@Param("id") Long id);
}
