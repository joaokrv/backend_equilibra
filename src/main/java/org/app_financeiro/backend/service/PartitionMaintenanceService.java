package org.app_financeiro.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 
 * Criação proativa de partições anuais e retenção de 3 anos de dados históricos.
 * A partição do ano atual é garantida via migrations do Flyway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartitionMaintenanceService {

    private final JdbcTemplate jdbcTemplate;

    /** 
     * Executado anualmente no dia 1º de Dezembro às 00:00.
     * Prepara a partição para o ano seguinte.
     */
    @Scheduled(cron = "0 0 0 1 12 *")
    @Transactional
    public void prepararParticaoAnual() {
        log.info("Iniciando manutenção autônoma de partições (Agendamento Anual)...");
        
        LocalDate hoje = LocalDate.now();
        int anoSeguinte = hoje.getYear() + 1;
        
        prepararParticao(anoSeguinte);
        
        log.info("Manutenção de partições concluída com sucesso.");
    }

    private void prepararParticao(int ano) {
        String dataInicio = ano + "-01-01";
        String dataFim = (ano + 1) + "-01-01";

        log.info("Verificando partições para o ano: {}", ano);

        try {
            String sqlIndicador = String.format(
                "CREATE TABLE IF NOT EXISTS indicador_economico_%d PARTITION OF indicador_economico " +
                "FOR VALUES FROM ('%s') TO ('%s')",
                ano, dataInicio, dataFim
            );
            jdbcTemplate.execute(sqlIndicador);
            log.info("Partição indicador_economico_{} verificada/criada.", ano);
        } catch (Exception e) {
            log.error("Falha ao criar partição indicador_economico_{}: {}", ano, e.getMessage());
        }

        try {
            String sqlPatrimonio = String.format(
                "CREATE TABLE IF NOT EXISTS patrimonio_historico_%d PARTITION OF patrimonio_historico " +
                "FOR VALUES FROM ('%s') TO ('%s')",
                ano, dataInicio, dataFim
            );
            jdbcTemplate.execute(sqlPatrimonio);
            log.info("Partição patrimonio_historico_{} verificada/criada.", ano);
        } catch (Exception e) {
            log.error("Falha ao criar partição patrimonio_historico_{}: {}", ano, e.getMessage());
        }
    }
    
    /** 
     * Remove partições com mais de 3 anos. Executado dia 1 de Janeiro às 02:00.
     */
    @Scheduled(cron = "0 0 2 1 1 *")
    @Transactional
    public void removerParticoesAntigas() {
        log.info("Iniciando limpeza de partições antigas (retenção: 3 anos)...");

        int anoLimite = LocalDate.now().getYear() - 3;

        String[] tabelas = {"indicador_economico", "patrimonio_historico"};
        for (String tabela : tabelas) {
            String nomeParticao = tabela + "_" + anoLimite;
            try {
                log.info("Verificando existência de partição antiga: {}", nomeParticao);
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + nomeParticao + " CASCADE");
                log.info("Partição {} removida (se existia).", nomeParticao);
            } catch (Exception e) {
                log.error("Falha ao remover partição {}: {}", nomeParticao, e.getMessage());
            }
        }

        log.info("Limpeza de partições antigas concluída.");
    }
}
