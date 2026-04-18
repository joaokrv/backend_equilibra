package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CartaoEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/** @SQLRestriction("ativo = true") filtra automaticamente em todas as queries derivadas. */
@Repository
public interface CartaoRepository extends JpaRepository<CartaoEntity, Long> {

    /** @EntityGraph evita N+1 ao carregar conta vinculada. */
    @EntityGraph(attributePaths = {"conta"})
    List<CartaoEntity> findByUsuarioId(Long usuarioId);

    /** PESSIMISTIC_WRITE — serializa atualizações de limite concorrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CartaoEntity c WHERE c.id = :id")
    Optional<CartaoEntity> findByIdWithLock(@Param("id") Long id);

        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE CartaoEntity c
                        SET c.conta = null
                        WHERE c.usuario.id = :usuarioId
                            AND c.conta IS NOT NULL
                            AND c.conta.id = :contaId
                        """)
        int desvincularConta(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId);
}
