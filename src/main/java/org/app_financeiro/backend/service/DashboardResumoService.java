package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.DashboardResumoPeriodoResponseDTO;
import org.app_financeiro.backend.enums.PeriodoDashboard;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.entity.PatrimonioHistoricoEntity;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.PatrimonioHistoricoRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Function;

/**
 * Consolida o resumo financeiro da dashboard em janelas de periodo.
 */
@Service
public class DashboardResumoService {

    private final TransacaoRepository transacaoRepository;
    private final ContaRepository contaRepository;
    private final InvestimentoRepository investimentoRepository;
    private final PatrimonioHistoricoRepository patrimonioHistoricoRepository;

    public DashboardResumoService(TransacaoRepository transacaoRepository,
                                  ContaRepository contaRepository,
                                  InvestimentoRepository investimentoRepository,
                                  PatrimonioHistoricoRepository patrimonioHistoricoRepository) {
        this.transacaoRepository = transacaoRepository;
        this.contaRepository = contaRepository;
        this.investimentoRepository = investimentoRepository;
        this.patrimonioHistoricoRepository = patrimonioHistoricoRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResumoPeriodoResponseDTO obterResumoPorPeriodo(Long usuarioId, PeriodoDashboard periodo) {
        IntervaloReferencia intervalo = calcularIntervalos(periodo);

        BigDecimal totalReceitasAtual = transacaoRepository.somarPorTipoNoPeriodo(
                usuarioId, intervalo.inicioAtual(), intervalo.fimAtual(), TipoTransacao.RECEITA);
        BigDecimal totalReceitasAnterior = transacaoRepository.somarPorTipoNoPeriodo(
                usuarioId, intervalo.inicioAnterior(), intervalo.fimAnterior(), TipoTransacao.RECEITA);
        BigDecimal totalReceitasPendentesAtual = transacaoRepository.somarPorTipoEStatusNoPeriodo(
                usuarioId, intervalo.inicioAtual(), intervalo.fimAtual(), TipoTransacao.RECEITA, StatusTransacao.PENDENTE);

        BigDecimal totalDespesasAtual = transacaoRepository.somarPorTipoNoPeriodo(
                usuarioId, intervalo.inicioAtual(), intervalo.fimAtual(), TipoTransacao.DESPESA);
        BigDecimal totalDespesasAnterior = transacaoRepository.somarPorTipoNoPeriodo(
                usuarioId, intervalo.inicioAnterior(), intervalo.fimAnterior(), TipoTransacao.DESPESA);
        BigDecimal totalDespesasPendentesAtual = transacaoRepository.somarPorTipoEStatusNoPeriodo(
                usuarioId, intervalo.inicioAtual(), intervalo.fimAtual(), TipoTransacao.DESPESA, StatusTransacao.PENDENTE);

        BigDecimal saldoContasAtual = normalizar(contaRepository.somarSaldoPorUsuario(usuarioId));
        BigDecimal totalInvestidoAtual = normalizar(investimentoRepository.somarTotalInvestidoPorUsuario(usuarioId));

        VariacaoSnapshot variacaoSaldo = calcularVariacaoSnapshot(
                usuarioId, intervalo, PatrimonioHistoricoEntity::getSaldoContas);
        VariacaoSnapshot variacaoInvestimentos = calcularVariacaoSnapshot(
                usuarioId, intervalo, PatrimonioHistoricoEntity::getTotalInvestido);

        return new DashboardResumoPeriodoResponseDTO(
                periodo.getCodigo(),
                intervalo.inicioAtual(),
                intervalo.fimAtual(),
                intervalo.inicioAnterior(),
                intervalo.fimAnterior(),
                totalReceitasAtual,
                totalReceitasAnterior,
                totalReceitasPendentesAtual,
                calcularVariacaoPercentual(totalReceitasAtual, totalReceitasAnterior),
                totalDespesasAtual,
                totalDespesasAnterior,
                totalDespesasPendentesAtual,
                calcularVariacaoPercentual(totalDespesasAtual, totalDespesasAnterior),
                saldoContasAtual,
                variacaoSaldo.valorAnterior(),
                totalInvestidoAtual,
                variacaoInvestimentos.valorAnterior(),
                variacaoSaldo.percentual(),
                variacaoInvestimentos.percentual()
        );
    }

    private IntervaloReferencia calcularIntervalos(PeriodoDashboard periodo) {
        YearMonth mesAtual = YearMonth.now();
        YearMonth inicioAtualMes = mesAtual.minusMonths(periodo.getQuantidadeMeses() - 1L);

        LocalDate inicioAtual = inicioAtualMes.atDay(1);
        LocalDate fimAtual = mesAtual.atEndOfMonth();

        YearMonth fimAnteriorMes = inicioAtualMes.minusMonths(1);
        YearMonth inicioAnteriorMes = inicioAtualMes.minusMonths(periodo.getQuantidadeMeses());

        LocalDate inicioAnterior = inicioAnteriorMes.atDay(1);
        LocalDate fimAnterior = fimAnteriorMes.atEndOfMonth();

        return new IntervaloReferencia(inicioAtual, fimAtual, inicioAnterior, fimAnterior);
    }

    private Double calcularVariacaoPercentual(BigDecimal atual, BigDecimal anterior) {
        if (anterior.compareTo(BigDecimal.ZERO) == 0) {
            if (atual.compareTo(BigDecimal.ZERO) == 0) {
                return 0.0;
            }
            return 100.0;
        }

        if (anterior.abs().compareTo(new BigDecimal("1.00")) < 0) {
            return null;
        }

        BigDecimal variacao = atual.subtract(anterior)
                .divide(anterior.abs(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        return variacao.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private VariacaoSnapshot calcularVariacaoSnapshot(Long usuarioId,
                                                      IntervaloReferencia intervalo,
                                                      Function<PatrimonioHistoricoEntity, BigDecimal> extrator) {
        var snapshotAtual = patrimonioHistoricoRepository.findMaisRecentePorUsuarioNoIntervalo(
                usuarioId, intervalo.inicioAtual(), intervalo.fimAtual());
        var snapshotAnterior = patrimonioHistoricoRepository.findMaisRecentePorUsuarioNoIntervalo(
                usuarioId, intervalo.inicioAnterior(), intervalo.fimAnterior());

        if (snapshotAtual.isEmpty() || snapshotAnterior.isEmpty()) {
            return new VariacaoSnapshot(null, null);
        }

        BigDecimal atual = extrator.apply(snapshotAtual.get());
        BigDecimal anterior = extrator.apply(snapshotAnterior.get());

        return new VariacaoSnapshot(
                calcularVariacaoPercentual(atual, anterior),
                anterior
        );
    }

    private BigDecimal normalizar(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private record IntervaloReferencia(
            LocalDate inicioAtual,
            LocalDate fimAtual,
            LocalDate inicioAnterior,
            LocalDate fimAnterior
    ) {
    }

    private record VariacaoSnapshot(
            Double percentual,
            BigDecimal valorAnterior
    ) {
    }
}
