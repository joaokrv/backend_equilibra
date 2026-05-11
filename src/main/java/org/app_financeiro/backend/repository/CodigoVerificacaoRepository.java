package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.enums.TipoCodigoVerificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodigoVerificacaoRepository extends JpaRepository<CodigoVerificacaoEntity, Long> {

    Optional<CodigoVerificacaoEntity> findTopByEmailAndIsUtilizadoFalseOrderByDataCriacaoDesc(String email);

    Optional<CodigoVerificacaoEntity> findTopByEmailAndTipoAndIsUtilizadoFalseOrderByDataCriacaoDesc(String email, TipoCodigoVerificacao tipo);

    Optional<CodigoVerificacaoEntity> findTopByEmailAndTipoOrderByDataCriacaoDesc(String email, TipoCodigoVerificacao tipo);

    /** Invalida em lote antes de gerar novo OTP — evita múltiplos OTPs ativos simultaneamente. */
    @Modifying
    @Query("UPDATE CodigoVerificacaoEntity c SET c.isUtilizado = true WHERE c.email = :email AND c.isUtilizado = false")
    void invalidarTodosPendentes(@Param("email") String email);

    @Modifying
    @Query("UPDATE CodigoVerificacaoEntity c SET c.isUtilizado = true WHERE c.email = :email AND c.tipo = :tipo AND c.isUtilizado = false")
    void invalidarTodosPendentesPorTipo(@Param("email") String email, @Param("tipo") TipoCodigoVerificacao tipo);

    void deleteByEmail(String email);

    Optional<CodigoVerificacaoEntity> findByEmailAndCodigoAndIsUtilizadoFalse(String email, String codigo);

    Optional<CodigoVerificacaoEntity> findByEmailAndCodigoAndTipoAndIsUtilizadoFalse(String email, String codigo, TipoCodigoVerificacao tipo);
}
