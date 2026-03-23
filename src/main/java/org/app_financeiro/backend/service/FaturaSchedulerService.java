package org.app_financeiro.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.repository.FaturaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Serviço responsável por tarefas agendadas relacionadas a Faturas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaturaSchedulerService {

    private final FaturaRepository faturaRepository;

    /**
     * Executa diariamente à meia-noite (00:00:00).
     * Verifica faturas ABERTAS ou FECHADAS cuja data de vencimento é anterior a hoje
     * e altera o status para ATRASADA.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(name = "atualizarFaturasAtrasadas", lockAtMostFor = "PT5M")
    public void atualizarFaturasAtrasadas() {
        log.info("Iniciando rotina de verificação de faturas atrasadas...");
        
        LocalDate hoje = LocalDate.now();
        int atualizadas = faturaRepository.marcarFaturasComoAtrasadas(hoje, StatusFatura.ATRASADA, StatusFatura.ABERTA, StatusFatura.FECHADA);
        
        log.info("Rotina de verificação concluída. {} faturas marcadas como ATRASADA.", atualizadas);
    }
}
