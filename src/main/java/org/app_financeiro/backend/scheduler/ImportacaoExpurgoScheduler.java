package org.app_financeiro.backend.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.app_financeiro.backend.enums.StatusImportacao;
import org.app_financeiro.backend.repository.ImportacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Remove sessões de importação PENDENTE, CANCELADA e PROCESSANDO com mais de 24h.
 * PROCESSANDO há >24h é lote abandonado por crash — bem além da janela de 10min de re-claim.
 * Sessões CONFIRMADA são mantidas para rastreabilidade do importacao_id nas transações.
 */
@Component
@RequiredArgsConstructor
public class ImportacaoExpurgoScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoExpurgoScheduler.class);
    private final ImportacaoRepository importacaoRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "ImportacaoExpurgoScheduler_expurgarSessoes", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional
    public void expurgarSessoes() {
        LocalDateTime limite = LocalDateTime.now().minusHours(24);
        int removidas = importacaoRepository.deletarSessoesAntigas(
                List.of(StatusImportacao.PENDENTE, StatusImportacao.CANCELADA, StatusImportacao.PROCESSANDO),
                limite);
        if (removidas > 0) {
            log.info("Expurgo de importações: {} sessões removidas (anteriores a {})", removidas, limite);
        }
    }
}
