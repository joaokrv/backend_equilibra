package org.app_financeiro.backend.repository;

import jakarta.persistence.LockModeType;
import org.app_financeiro.backend.entity.UsuarioPendenteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioPendenteRepository extends JpaRepository<UsuarioPendenteEntity, UUID> {

    Optional<UsuarioPendenteEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UsuarioPendenteEntity u WHERE u.id = :id")
    Optional<UsuarioPendenteEntity> findByIdWithLock(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM UsuarioPendenteEntity u WHERE u.expiraEm < :data AND (u.bloqueadoAte IS NULL OR u.bloqueadoAte < :data)")
    void deleteExpiredAndNotLocked(@Param("data") LocalDateTime data);
}
