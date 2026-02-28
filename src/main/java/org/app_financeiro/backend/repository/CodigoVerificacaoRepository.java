package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodigoVerificacaoRepository extends JpaRepository<CodigoVerificacaoEntity, Long> {

    /**
     * Busca o código de verificação mais recente para um e-mail, que ainda não foi utilizado.
     */
    Optional<CodigoVerificacaoEntity> findTopByEmailAndUtilizadoFalseOrderByDataCriacaoDesc(String email);

    /**
     * Busca por e-mail e código específico, que ainda não foi utilizado.
     */
    Optional<CodigoVerificacaoEntity> findByEmailAndCodigoAndUtilizadoFalse(String email, String codigo);
}
