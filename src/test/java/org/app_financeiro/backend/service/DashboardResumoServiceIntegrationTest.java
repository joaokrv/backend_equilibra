package org.app_financeiro.backend.service;

import org.app_financeiro.backend.AbstractIntegrationTest;
import org.app_financeiro.backend.dto.response.DashboardResumoPeriodoResponseDTO;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.PeriodoDashboard;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardResumoServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DashboardResumoService dashboardResumoService;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UsuarioEntity usuario;

    @BeforeEach
    void setUp() {
        transacaoRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = new UsuarioEntity();
        usuario.setNome("Dashboard Test");
        usuario.setEmail("dashboard-resumo-integration@email.com");
        usuario.setSenha("SenhaSegura123");
        usuario = usuarioRepository.save(usuario);
    }

    @Test
    void deveRetornarTotaisCorretosParaPeriodo1M() {
        criarTransacao(TipoTransacao.RECEITA, StatusTransacao.PAGO, new BigDecimal("1000.00"), LocalDate.now());
        criarTransacao(TipoTransacao.RECEITA, StatusTransacao.PENDENTE, new BigDecimal("500.00"), LocalDate.now());
        criarTransacao(TipoTransacao.DESPESA, StatusTransacao.PAGO, new BigDecimal("300.00"), LocalDate.now());

        LocalDate mesAnterior = LocalDate.now().minusMonths(1).withDayOfMonth(15);
        criarTransacao(TipoTransacao.RECEITA, StatusTransacao.PAGO, new BigDecimal("800.00"), mesAnterior);

        DashboardResumoPeriodoResponseDTO resultado =
                dashboardResumoService.obterResumoPorPeriodo(usuario.getId(), PeriodoDashboard.UM_MES);

        assertThat(resultado.totalReceitasAtual()).isEqualByComparingTo("1500.00");
        assertThat(resultado.totalReceitasPendentesAtual()).isEqualByComparingTo("500.00");
        assertThat(resultado.totalDespesasAtual()).isEqualByComparingTo("300.00");
        assertThat(resultado.totalReceitasAnterior()).isEqualByComparingTo("800.00");
        assertThat(resultado.variacaoReceitasPercentual()).isGreaterThan(0.0);
    }

    @Test
    void deveRetornarZerosParaPeriodoSemDados() {
        DashboardResumoPeriodoResponseDTO resultado =
                dashboardResumoService.obterResumoPorPeriodo(usuario.getId(), PeriodoDashboard.TRES_MESES);

        assertThat(resultado.totalReceitasAtual()).isEqualByComparingTo("0");
        assertThat(resultado.totalDespesasAtual()).isEqualByComparingTo("0");
        assertThat(resultado.variacaoReceitasPercentual()).isEqualTo(0.0);
        assertThat(resultado.variacaoDespesasPercentual()).isEqualTo(0.0);
    }

    @Test
    void deveCalcularVariacao1AComViradaDeAno() {
        criarTransacao(TipoTransacao.DESPESA, StatusTransacao.PAGO, new BigDecimal("600.00"), LocalDate.now());

        LocalDate anoPassado = LocalDate.now().minusMonths(13).withDayOfMonth(10);
        criarTransacao(TipoTransacao.DESPESA, StatusTransacao.PAGO, new BigDecimal("400.00"), anoPassado);

        DashboardResumoPeriodoResponseDTO resultado =
                dashboardResumoService.obterResumoPorPeriodo(usuario.getId(), PeriodoDashboard.UM_ANO);

        assertThat(resultado.totalDespesasAtual()).isEqualByComparingTo("600.00");
        assertThat(resultado.totalDespesasAnterior()).isEqualByComparingTo("400.00");
        assertThat(resultado.variacaoDespesasPercentual()).isEqualTo(50.0);
    }

    private void criarTransacao(TipoTransacao tipo, StatusTransacao status, BigDecimal valor, LocalDate data) {
        TransacaoEntity transacao = new TransacaoEntity();
        transacao.setUsuario(usuario);
        transacao.setTipo(tipo);
        transacao.setStatus(status);
        transacao.setValor(valor);
        transacao.setData(data);
        transacao.setDescricao("Teste " + tipo.name());
        transacao.setIdempotencyKey(UUID.randomUUID().toString());
        transacao.setAtivo(true);

        transacaoRepository.save(transacao);
    }
}