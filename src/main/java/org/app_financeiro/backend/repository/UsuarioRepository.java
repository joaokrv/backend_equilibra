package org.app_financeiro.backend.repository;

import jakarta.persistence.LockModeType;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** @SQLRestriction("ativo = true") filtra automaticamente em todas as queries derivadas. */
@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {

    Optional<UsuarioEntity> findByEmail(String email);

    /**
     * PESSIMISTIC_WRITE — serializa login/refresh/logout concorrentes para o mesmo usuário.
     * Requer @Transactional no chamador.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UsuarioEntity u WHERE u.email = :email")
    Optional<UsuarioEntity> findByEmailWithLock(@Param("email") String email);

    Optional<UsuarioEntity> findByCelular(String celular);

    /** Query nativa bypassa @SQLRestriction — verifica duplicidade incluindo inativos. */
    @Query(value = "SELECT COUNT(*) > 0 FROM usuarios u WHERE u.email = :email", nativeQuery = true)
    boolean existsByEmailIncludingInactive(String email);

    /** Query nativa bypassa @SQLRestriction — usado para enviar avisos a contas ativas ou desativadas. */
    @Query(value = "SELECT * FROM usuarios u WHERE u.email = :email", nativeQuery = true)
    Optional<UsuarioEntity> findByEmailIncludingInactive(String email);

    /** Query nativa bypassa @SQLRestriction — exclusivo para reativação de conta. */
    @Query(value = "SELECT * FROM usuarios u WHERE u.email = :email AND u.ativo = false", nativeQuery = true)
    Optional<UsuarioEntity> findInactiveByEmail(String email);
}
