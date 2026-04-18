package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.TokenRecuperacaoSenhaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRecuperacaoSenhaRepository extends JpaRepository<TokenRecuperacaoSenhaEntity, Long> {

    Optional<TokenRecuperacaoSenhaEntity> findByTokenAndIsUtilizadoFalse(String token);

    /** Invalida tokens anteriores antes de gerar novo — garante single-token-at-a-time. */
    @Modifying
    @Query("UPDATE TokenRecuperacaoSenhaEntity t SET t.isUtilizado = true WHERE t.email = :email AND t.isUtilizado = false")
    void invalidarTokensAnteriores(String email);

    /** Usado para validar o throttle (tempo de espera) entre solicitações. */
    java.util.Optional<TokenRecuperacaoSenhaEntity> findFirstByEmailOrderByDataCriacaoDesc(String email);
}
