package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.DashboardResumoPeriodoResponseDTO;
import org.app_financeiro.backend.entity.PatrimonioHistoricoEntity;
import org.app_financeiro.backend.enums.PeriodoDashboard;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.PatrimonioHistoricoRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardResumoServiceTest {

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private ContaRepository contaRepository;

    @Mock
    private InvestimentoRepository investimentoRepository;

    @Mock
    private PatrimonioHistoricoRepository patrimonioHistoricoRepository;

    @InjectMocks
    private DashboardResumoService dashboardResumoService;

    private static final Long USUARIO_ID = 1L;

    @Test
    void deveRetornarResumoPara1MComVariacoesCorretas() {
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA)))
                .thenReturn(new BigDecimal("5000.00"))
                .thenReturn(new BigDecimal("4000.00"));
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(new BigDecimal("500.00"));
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA)))
                .thenReturn(new BigDecimal("3000.00"))
                .thenReturn(new BigDecimal("3000.00"));
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(new BigDecimal("200.00"));
        when(contaRepository.somarSaldoPorUsuario(USUARIO_ID)).thenReturn(new BigDecimal("10000.00"));
        when(investimentoRepository.somarTotalInvestidoPorUsuario(USUARIO_ID)).thenReturn(new BigDecimal("25000.00"));
        when(patrimonioHistoricoRepository.findMaisRecentePorUsuarioNoIntervalo(eq(USUARIO_ID), any(), any()))
                .thenReturn(Optional.empty());

        DashboardResumoPeriodoResponseDTO resultado =
                dashboardResumoService.obterResumoPorPeriodo(USUARIO_ID, PeriodoDashboard.UM_MES);

        assertThat(resultado.periodo()).isEqualTo("1M");
        assertThat(resultado.totalReceitasAtual()).isEqualByComparingTo("5000.00");
        assertThat(resultado.totalReceitasAnterior()).isEqualByComparingTo("4000.00");
        assertThat(resultado.totalReceitasPendentesAtual()).isEqualByComparingTo("500.00");
        assertThat(resultado.variacaoReceitasPercentual()).isEqualTo(25.0);
        assertThat(resultado.totalDespesasAtual()).isEqualByComparingTo("3000.00");
        assertThat(resultado.variacaoDespesasPercentual()).isEqualTo(0.0);
        assertThat(resultado.saldoContasAtual()).isEqualByComparingTo("10000.00");
        assertThat(resultado.totalInvestidoAtual()).isEqualByComparingTo("25000.00");
        assertThat(resultado.variacaoSaldoContasPercentual()).isNull();
        assertThat(resultado.variacaoInvestimentosPercentual()).isNull();
    }

    @Test
    void deveRetornarVariacaoZeroQuandoAnteriorEAtualSaoZero() {
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA)))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA)))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(BigDecimal.ZERO);
        when(contaRepository.somarSaldoPorUsuario(USUARIO_ID)).thenReturn(null);
        when(investimentoRepository.somarTotalInvestidoPorUsuario(USUARIO_ID)).thenReturn(null);
        when(patrimonioHistoricoRepository.findMaisRecentePorUsuarioNoIntervalo(eq(USUARIO_ID), any(), any()))
                .thenReturn(Optional.empty());

        DashboardResumoPeriodoResponseDTO resultado =
                dashboardResumoService.obterResumoPorPeriodo(USUARIO_ID, PeriodoDashboard.UM_MES);

        assertThat(resultado.variacaoReceitasPercentual()).isEqualTo(0.0);
        assertThat(resultado.variacaoDespesasPercentual()).isEqualTo(0.0);
        assertThat(resultado.saldoContasAtual()).isEqualByComparingTo("0");
        assertThat(resultado.totalInvestidoAtual()).isEqualByComparingTo("0");
    }

    @Test
    void deveRetornar100QuandoAnteriorZeroEAtualPositivo() {
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA)))
                .thenReturn(new BigDecimal("1000.00"))
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA)))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(BigDecimal.ZERO);
        when(contaRepository.somarSaldoPorUsuario(USUARIO_ID)).thenReturn(BigDecimal.ZERO);
        when(investimentoRepository.somarTotalInvestidoPorUsuario(USUARIO_ID)).thenReturn(BigDecimal.ZERO);
        when(patrimonioHistoricoRepository.findMaisRecentePorUsuarioNoIntervalo(eq(USUARIO_ID), any(), any()))
                .thenReturn(Optional.empty());

        DashboardResumoPeriodoResponseDTO resultado =
                dashboardResumoService.obterResumoPorPeriodo(USUARIO_ID, PeriodoDashboard.UM_MES);

        assertThat(resultado.variacaoReceitasPercentual()).isEqualTo(100.0);
    }

    @Test
    void deveCalcularVariacaoDeSnapshotsQuandoExistemDoisPeriodos() {
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA)))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA)))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(BigDecimal.ZERO);
        when(contaRepository.somarSaldoPorUsuario(USUARIO_ID)).thenReturn(new BigDecimal("10000.00"));
        when(investimentoRepository.somarTotalInvestidoPorUsuario(USUARIO_ID)).thenReturn(new BigDecimal("5000.00"));

        PatrimonioHistoricoEntity snapshotAtual = new PatrimonioHistoricoEntity();
        snapshotAtual.setSaldoContas(new BigDecimal("10000.00"));
        snapshotAtual.setTotalInvestido(new BigDecimal("5000.00"));

        PatrimonioHistoricoEntity snapshotAnterior = new PatrimonioHistoricoEntity();
        snapshotAnterior.setSaldoContas(new BigDecimal("8000.00"));
        snapshotAnterior.setTotalInvestido(new BigDecimal("4000.00"));

        when(patrimonioHistoricoRepository.findMaisRecentePorUsuarioNoIntervalo(eq(USUARIO_ID), any(), any()))
                .thenReturn(Optional.of(snapshotAtual))
                .thenReturn(Optional.of(snapshotAnterior))
                .thenReturn(Optional.of(snapshotAtual))
                .thenReturn(Optional.of(snapshotAnterior));

        DashboardResumoPeriodoResponseDTO resultado =
                dashboardResumoService.obterResumoPorPeriodo(USUARIO_ID, PeriodoDashboard.UM_MES);

        assertThat(resultado.variacaoSaldoContasPercentual()).isEqualTo(25.0);
        assertThat(resultado.variacaoInvestimentosPercentual()).isEqualTo(25.0);
    }

    @Test
    void deveRetornarIntervalosCorretosPara1A() {
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA)))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoNoPeriodo(eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA)))
                .thenReturn(BigDecimal.ZERO)
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.RECEITA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(BigDecimal.ZERO);
        when(transacaoRepository.somarPorTipoEStatusNoPeriodo(
                eq(USUARIO_ID), any(), any(), eq(TipoTransacao.DESPESA), eq(StatusTransacao.PENDENTE)))
                .thenReturn(BigDecimal.ZERO);
        when(contaRepository.somarSaldoPorUsuario(USUARIO_ID)).thenReturn(BigDecimal.ZERO);
        when(investimentoRepository.somarTotalInvestidoPorUsuario(USUARIO_ID)).thenReturn(BigDecimal.ZERO);
        when(patrimonioHistoricoRepository.findMaisRecentePorUsuarioNoIntervalo(eq(USUARIO_ID), any(), any()))
                .thenReturn(Optional.empty());

        DashboardResumoPeriodoResponseDTO resultado =
                dashboardResumoService.obterResumoPorPeriodo(USUARIO_ID, PeriodoDashboard.UM_ANO);

        assertThat(resultado.periodo()).isEqualTo("1A");
        assertThat(resultado.inicioPeriodoAtual()).isBefore(resultado.fimPeriodoAtual());
        assertThat(resultado.inicioPeriodoAnterior()).isBefore(resultado.fimPeriodoAnterior());
        assertThat(resultado.fimPeriodoAnterior()).isBefore(resultado.inicioPeriodoAtual());
    }
}