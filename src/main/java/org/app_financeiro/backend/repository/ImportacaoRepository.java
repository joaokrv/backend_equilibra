package org.app_financeiro.backend.repository;

import jakarta.persistence.LockModeType;
import org.app_financeiro.backend.entity.ImportacaoEntity;
import org.app_financeiro.backend.enums.StatusImportacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImportacaoRepository extends JpaRepository<ImportacaoEntity, UUID> {

    /** Expurgo: remove sessões PENDENTE/CANCELADA/PROCESSANDO mais antigas que o limite configurado. */
    @Modifying
    @Query("DELETE FROM ImportacaoEntity i WHERE i.status IN (:statuses) AND i.dataCriacao < :limite")
    int deletarSessoesAntigas(
            @Param("statuses") List<StatusImportacao> statuses,
            @Param("limite") LocalDateTime limite);

    /**
     * Claim atômico: só reivindica sessões PENDENTE ou PROCESSANDO abandonadas (crash no meio
     * do lote) há mais que {@code limite}. Retorno 0 = já confirmada ou sendo processada agora
     * por outra requisição — o chamador não deve iniciar o lote.
     */
    @Modifying
    @Transactional
    @Query("UPDATE ImportacaoEntity i SET i.status = :processando, i.processandoEm = :agora " +
           "WHERE i.id = :id AND (i.status = :pendente " +
           "OR (i.status = :processando AND i.processandoEm < :limite))")
    int reivindicarParaConfirmacao(
            @Param("id") UUID id,
            @Param("agora") LocalDateTime agora,
            @Param("limite") LocalDateTime limite,
            @Param("pendente") StatusImportacao pendente,
            @Param("processando") StatusImportacao processando);

    /** Fecha o claim: só transiciona quem está de fato PROCESSANDO (defesa em profundidade). */
    @Modifying
    @Transactional
    @Query("UPDATE ImportacaoEntity i SET i.status = :confirmada, i.candidatas = null " +
           "WHERE i.id = :id AND i.status = :processando")
    int finalizarConfirmacao(
            @Param("id") UUID id,
            @Param("confirmada") StatusImportacao confirmada,
            @Param("processando") StatusImportacao processando);

    /** Cancela apenas se ainda PENDENTE — falha silenciosamente (retorno 0) se já reivindicada/confirmada. */
    @Modifying
    @Transactional
    @Query("UPDATE ImportacaoEntity i SET i.status = :cancelada, i.candidatas = null " +
           "WHERE i.id = :id AND i.status = :pendente")
    int cancelarSePendente(
            @Param("id") UUID id,
            @Param("cancelada") StatusImportacao cancelada,
            @Param("pendente") StatusImportacao pendente);

    /** PESSIMISTIC_WRITE — serializa desfazerImportacao concorrente na mesma sessão. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ImportacaoEntity i WHERE i.id = :id")
    Optional<ImportacaoEntity> findByIdWithLock(@Param("id") UUID id);
}
