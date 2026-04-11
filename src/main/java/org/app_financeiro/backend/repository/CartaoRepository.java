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
    @EntityGraph(attributePaths = {"conta"})
    List<CartaoEntity> findByUsuarioId(Long usuarioId);

    /**
     * Busca um cartão específico com bloqueio pessimista.
     * Garante que nenhuma outra thread possa ler ou modificar o limite
     * do cartão até que a transação atual seja concluída.
     *
     * @param id ID do cartão
     * @return Optional contendo o cartão com lock
     */
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
