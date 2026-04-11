package org.app_financeiro.backend.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.app_financeiro.backend.repository.IndicadorEconomicoRepository;
import org.app_financeiro.backend.service.MarketDataService;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Orquestrador de agendamentos para dados de mercado.
 * Centraliza os gatilhos temporais para sincronização de indicadores.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataScheduler {

    private final MarketDataService marketDataService;
    private final IndicadorEconomicoRepository indicadorRepository;

    /**
     * Sincronização inicial ao subir a aplicação.
     * Se o banco estiver vazio, busca os dados agora em vez de esperar o cron.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void sincronizarAoIniciar() {
        if (indicadorRepository.count() == 0) {
            log.debug("Banco de indicadores vazio — executando sincronização inicial...");

            try {
                marketDataService.syncIndicadoresMacro();
            } catch (Exception e) {
                log.warn("Falha na sincronização inicial de indicadores macro. A aplicação continuará normalmente: {}", e.getMessage());
            }

            try {
                marketDataService.syncIPCA();
            } catch (Exception e) {
                log.warn("Falha na sincronização inicial do IPCA. A aplicação continuará normalmente: {}", e.getMessage());
            }
        }
    }

    /**
     * Sincronização de indicadores macro (SELIC, CDI, Câmbio, IBOVESPA, IFIX).
     * Executa 2x ao dia — às 01:00 (captura fechamento) e às 13:00 (meio do pregão).
     */
    @Scheduled(cron = "0 0 1,13 * * *")
    public void agendarSincronizacaoDiaria() {
        log.debug("Iniciando agendamento de indicadores macro...");
        marketDataService.syncIndicadoresMacro();
    }

    /**
     * Sincronização do IPCA acumulado 12 meses via BCB SGS (série 13522).
     * Executa mensalmente — dados são atualizados pelo IBGE uma vez por mês.
     */
    @Scheduled(cron = "0 0 2 1 * *")
    public void agendarSincronizacaoIPCA() {
        log.debug("Iniciando agendamento mensal de IPCA...");
        marketDataService.syncIPCA();
    }
}
