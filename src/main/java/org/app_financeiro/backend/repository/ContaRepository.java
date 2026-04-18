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

/** @SQLRestriction("ativo = true") filtra automaticamente em todas as queries derivadas. */
@Repository
public interface ContaRepository extends JpaRepository<ContaEntity, Long> {

    @Query("SELECT SUM(c.saldo) FROM ContaEntity c WHERE c.usuario.id = :usuarioId")
    BigDecimal somarSaldoPorUsuario(@Param("usuarioId") Long usuarioId);

    List<ContaEntity> findByUsuarioId(Long usuarioId);

    /** PESSIMISTIC_WRITE — serializa atualizações de saldo concorrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContaEntity c WHERE c.id = :id")
    Optional<ContaEntity> findByIdWithLock(@Param("id") Long id);
}
