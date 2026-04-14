package org.app_financeiro.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.app_financeiro.backend.entity.PatrimonioHistoricoEntity;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.PatrimonioHistoricoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PatrimonioHistoricoService {

    private final PatrimonioHistoricoRepository patrimonioHistoricoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContaRepository contaRepository;
    private final InvestimentoRepository investimentoRepository;

    /**
     * Popula snapshots no boot quando a base ainda está vazia,
     * evitando dashboard sem histórico até o primeiro cron diário.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void sincronizacaoInicialSeNecessario() {
        if (patrimonioHistoricoRepository.count() == 0) {
            log.info("Histórico de patrimônio vazio no boot. Executando sincronização inicial...");
            executarSnapshotsDiarios();
        }
    }

    /**
     * Motor de Snapshots Diários.
     * Executa todo dia à meia-noite (00:00).
     * Referencia o patrimônio consolidado do dia que se encerrou.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @SchedulerLock(name = "SnapshotPatrimonioDiario", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional
    public void executarSnapshotsDiarios() {
        log.info("Iniciando motor de snapshots diários de patrimônio...");

        // Buscamos todos os usuários ativos (filtrados automaticamente pelo @SQLRestriction)
        usuarioRepository.findAll().forEach(usuario -> {
            try {
                atualizarSnapshotUsuarioHoje(usuario.getId());
            } catch (Exception e) {
                log.error("Falha ao gerar snapshot para usuario {}: {}", usuario.getId(), e.getMessage());
            }
        });

        log.info("Motor de snapshots diários concluído com sucesso.");
    }

    /**
     * Busca dados para o gráfico de evolução patrimonial.
     * @param dias quantidade de dias passados a recuperar (ex: 30)
     */
    @Transactional
    public List<PatrimonioHistoricoEntity> buscarEvolucao(Long usuarioId, int dias) {
        atualizarSnapshotUsuarioHoje(usuarioId);
        LocalDate fim = LocalDate.now();
        LocalDate inicio = fim.minusDays(dias);
        return patrimonioHistoricoRepository.findByUsuarioIdAndDataReferenciaBetweenOrderByDataReferenciaAsc(
                usuarioId, inicio, fim);
    }

    @Transactional
    public void atualizarSnapshotUsuarioHoje(Long usuarioId) {
        var usuarioOpt = usuarioRepository.findById(usuarioId);
        if (usuarioOpt.isEmpty()) {
            return;
        }

        LocalDate hoje = LocalDate.now();
        BigDecimal saldoContas = contaRepository.somarSaldoPorUsuario(usuarioId);
        BigDecimal valorInvestido = investimentoRepository.somarTotalInvestidoPorUsuario(usuarioId);

        BigDecimal saldoContasNorm = saldoContas != null ? saldoContas : BigDecimal.ZERO;
        BigDecimal valorInvestidoNorm = valorInvestido != null ? valorInvestido : BigDecimal.ZERO;
        BigDecimal total = saldoContasNorm.add(valorInvestidoNorm);

        PatrimonioHistoricoEntity snapshot = patrimonioHistoricoRepository
                .findByUsuarioIdAndDataReferencia(usuarioId, hoje)
                .orElse(new PatrimonioHistoricoEntity());

        if (snapshot.getId() == null) {
            snapshot.setId(patrimonioHistoricoRepository.nextId());
        }

        snapshot.setUsuario(usuarioOpt.get());
        snapshot.setDataReferencia(hoje);
        snapshot.setValorTotal(total);
        snapshot.setSaldoContas(saldoContasNorm);
        snapshot.setTotalInvestido(valorInvestidoNorm);

        patrimonioHistoricoRepository.save(snapshot);
        log.debug("Snapshot atualizado para usuario {}: total={}, contas={}, investido={}",
                usuarioId, total, saldoContasNorm, valorInvestidoNorm);
    }
}
