package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.NotificacaoFaturaEntity;
import org.app_financeiro.backend.enums.StatusNotificacaoFatura;
import org.app_financeiro.backend.enums.TipoLembreteFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacaoFaturaRepository extends JpaRepository<NotificacaoFaturaEntity, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<NotificacaoFaturaEntity> findByFaturaIdAndTipoAndStatus(
            Long faturaId, TipoLembreteFatura tipo, StatusNotificacaoFatura status);
}
