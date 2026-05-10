package org.app_financeiro.backend.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduler para limpeza de pré-registros expirados.
 * Garante que a tabela 'usuarios_pendentes' não cresça indefinidamente.
 */
@Component
@RequiredArgsConstructor
public class UsuarioPendenteCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UsuarioPendenteCleanupScheduler.class);
    private final UsuarioPendenteRepository usuarioPendenteRepository;

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "UsuarioPendenteCleanupScheduler_limparExpirados", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional
    public void limparExpirados() {
        log.info("Iniciando limpeza de pré-registros expirados...");
        
        try {
            LocalDateTime agora = LocalDateTime.now();
            usuarioPendenteRepository.deleteExpiredAndNotLocked(agora);
            log.info("Limpeza de pré-registros concluída com sucesso.");
        } catch (Exception e) {
            log.error("Erro durante a limpeza de pré-registros: {}", e.getMessage());
        }
    }
}
