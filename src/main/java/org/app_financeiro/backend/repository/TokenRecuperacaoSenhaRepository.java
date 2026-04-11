package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.TokenRecuperacaoSenhaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository para persistência dos tokens de recuperação de senha.
 *
 * Métodos personalizados:
 * - Busca por token válido (não utilizado)
 * - Invalidação em massa de tokens anteriores por e-mail
 */
@Repository
public interface TokenRecuperacaoSenhaRepository extends JpaRepository<TokenRecuperacaoSenhaEntity, Long> {

    /**
     * Busca um token de recuperação pelo valor do token, somente se ainda não foi utilizado.
     *
     * @param token UUID do token
     * @return Optional com a entidade do token, se encontrado
     */
    Optional<TokenRecuperacaoSenhaEntity> findByTokenAndIsUtilizadoFalse(String token);

    /**
     * Invalida (marca como utilizado) todos os tokens pendentes de um e-mail.
     * Chamado antes de gerar um novo token para garantir single-token-at-a-time.
     *
     * @param email E-mail do usuário
     */
    @Modifying
    @Query("UPDATE TokenRecuperacaoSenhaEntity t SET t.isUtilizado = true WHERE t.email = :email AND t.isUtilizado = false")
    void invalidarTokensAnteriores(String email);

    /**
     * Busca o token mais recente criado para um determinado e-mail.
     * Usado para validar o tempo de espera (throttle) entre solicitações.
     *
     * @param email E-mail do usuário
     * @return Optional com o token mais recente
     */
    java.util.Optional<TokenRecuperacaoSenhaEntity> findFirstByEmailOrderByDataCriacaoDesc(String email);
}
