package org.app_financeiro.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Criação proativa de partições mensais e retenção de 2 anos de dados históricos. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartitionMaintenanceService {

    private final JdbcTemplate jdbcTemplate;
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM");

    /** Cria partições dos próximos 2 meses. Executado dia 25 às 01:00. */
    @Scheduled(cron = "0 0 1 25 * *")
    @Transactional
    public void gerenciarParticoes() {
        log.info("Iniciando manutenção autônoma de partições...");
        
        LocalDate hoje = LocalDate.now();
        
        prepararParticao(hoje.plusMonths(1));
        prepararParticao(hoje.plusMonths(2));
        
        log.info("Manutenção de partições concluída com sucesso.");
    }

    private void prepararParticao(LocalDate data) {
        String sufixo = data.format(YEAR_MONTH_FORMATTER);
        LocalDate inicioMes = data.withDayOfMonth(1);
        LocalDate inicioProximoMes = inicioMes.plusMonths(1);

        // SEGURANÇA: sufixo e datas derivados exclusivamente de LocalDate.now() — sem input externo.
        // Defesa em profundidade contra SQL injection caso alguém refatore este método (B3-A1).
        if (!sufixo.matches("^\\d{4}_\\d{2}$")) {
            throw new IllegalStateException("Sufixo de partição inválido: " + sufixo);
        }
        if (!inicioMes.toString().matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            throw new IllegalStateException("Data de partição inválida: " + inicioMes);
        }

        log.info("Verificando partição para o período: {}", sufixo);

        try {
            String sqlIndicador = String.format(
                "CREATE TABLE IF NOT EXISTS indicador_economico_%s PARTITION OF indicador_economico " +
                "FOR VALUES FROM ('%s') TO ('%s')",
                sufixo, inicioMes, inicioProximoMes
            );
            jdbcTemplate.execute(sqlIndicador);
            log.info("Partição indicador_economico_{} verificada/criada.", sufixo);
        } catch (Exception e) {
            log.error("Falha ao criar partição indicador_economico_{}: {}", sufixo, e.getMessage());
        }

        try {
            String sqlPatrimonio = String.format(
                "CREATE TABLE IF NOT EXISTS patrimonio_historico_%s PARTITION OF patrimonio_historico " +
                "FOR VALUES FROM ('%s') TO ('%s')",
                sufixo, inicioMes, inicioProximoMes
            );
            jdbcTemplate.execute(sqlPatrimonio);
            log.info("Partição patrimonio_historico_{} verificada/criada.", sufixo);
        } catch (Exception e) {
            log.error("Falha ao criar partição patrimonio_historico_{}: {}", sufixo, e.getMessage());
        }
    }
    
    /** Remove partições com mais de 2 anos. Executado dia 1 às 02:00. */
    @Scheduled(cron = "0 0 2 1 * *")
    @Transactional
    public void removerParticoesAntigas() {
        log.info("Iniciando limpeza de partições antigas (retenção: 2 anos)...");

        YearMonth limite = YearMonth.now().minusYears(2);
        String sufixo = limite.format(YEAR_MONTH_FORMATTER);

        String[] tabelas = {"indicador_economico", "patrimonio_historico"};
        for (String tabela : tabelas) {
            String nomeParticao = tabela + "_" + sufixo;
            try {
                log.info("Verificando existência de partição antiga: {}", nomeParticao);
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + nomeParticao);
                log.info("Partição {} removida (se existia).", nomeParticao);
            } catch (Exception e) {
                log.error("Falha ao remover partição {}: {}", nomeParticao, e.getMessage());
            }
        }

        log.info("Limpeza de partições antigas concluída.");
    }
}
