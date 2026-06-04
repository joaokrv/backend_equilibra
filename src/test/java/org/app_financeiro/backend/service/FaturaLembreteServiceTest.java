package org.app_financeiro.backend.service;

import org.app_financeiro.backend.AbstractIntegrationTest;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.enums.StatusNotificacaoFatura;
import org.app_financeiro.backend.enums.TipoLembreteFatura;
import org.app_financeiro.backend.repository.CartaoRepository;
import org.app_financeiro.backend.repository.FaturaRepository;
import org.app_financeiro.backend.repository.NotificacaoFaturaRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FaturaLembreteServiceTest extends AbstractIntegrationTest {

    @Autowired private FaturaLembreteService faturaLembreteService;
    @Autowired private FaturaRepository faturaRepository;
    @Autowired private NotificacaoFaturaRepository notificacaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CartaoRepository cartaoRepository;

    private UsuarioEntity usuario;
    private CartaoEntity cartao;

    @BeforeEach
    void setUp() throws Exception {
        limparTodasAsTabelas();

        setupUsuarioVerificado("João Teste", "joao.lembrete@teste.com", "Senha@123");
        usuario = usuarioRepository.findByEmail("joao.lembrete@teste.com").orElseThrow();
        usuario.setNotificacoesFaturaAtivo(true);
        usuarioRepository.save(usuario);

        cartao = new CartaoEntity();
        cartao.setNome("Nubank Teste");
        cartao.setDiaFechamento(5);
        cartao.setDiaVencimento(15);
        cartao.setLimite(new BigDecimal("5000.00"));
        cartao.setUsuario(usuario);
        cartao = cartaoRepository.save(cartao);

        // Limpa invocações registradas durante setupUsuarioVerificado (envio do OTP)
        // para que assertions como verifyNoInteractions() não incluam chamadas do setUp
        clearInvocations(externalEmailSenderService);
    }

    @Test
    void deveEnviarLembreteD3() {
        FaturaEntity fatura = criarFatura(LocalDate.now().plusDays(3), StatusFatura.ABERTA);

        var resultado = faturaLembreteService.executarJob();

        assertThat(resultado.sent()).isEqualTo(1);
        assertThat(resultado.errors()).isZero();
        verify(externalEmailSenderService, times(1)).sendHtml(anyString(), contains("3 dias"), anyString());

        var notificacoes = notificacaoRepository.findAll();
        assertThat(notificacoes).hasSize(1);
        assertThat(notificacoes.get(0).getStatus()).isEqualTo(StatusNotificacaoFatura.ENVIADO);
        assertThat(notificacoes.get(0).getTipo()).isEqualTo(TipoLembreteFatura.D_3);
    }

    @Test
    void deveEnviarLembreteD1() {
        criarFatura(LocalDate.now().plusDays(1), StatusFatura.FECHADA);

        var resultado = faturaLembreteService.executarJob();

        assertThat(resultado.sent()).isEqualTo(1);
        verify(externalEmailSenderService, times(1)).sendHtml(anyString(), contains("amanhã"), anyString());
    }

    @Test
    void deveEnviarLembreteD0() {
        criarFatura(LocalDate.now(), StatusFatura.FECHADA);

        var resultado = faturaLembreteService.executarJob();

        assertThat(resultado.sent()).isEqualTo(1);
        verify(externalEmailSenderService, times(1)).sendHtml(anyString(), contains("hoje"), anyString());
    }

    @Test
    void deveEnviarLembreteAtrasoUmaVez() {
        criarFatura(LocalDate.now().minusDays(5), StatusFatura.ATRASADA);

        var resultado1 = faturaLembreteService.executarJob();
        assertThat(resultado1.sent()).isEqualTo(1);

        // Segunda execução: fatura excluída pelo NOT EXISTS na query (notificação ENVIADO já existe)
        // processed=0 porque a query já não retorna a fatura (diferente de D-3/D-1/D0 que usam existsByIdempotencyKey)
        var resultado2 = faturaLembreteService.executarJob();
        assertThat(resultado2.sent()).isZero();
        assertThat(resultado2.processed()).isZero();

        verify(externalEmailSenderService, times(1)).sendHtml(anyString(), contains("atraso"), anyString());
    }

    @Test
    void naoDeveEnviarSeIdempotenciaJaExiste() {
        criarFatura(LocalDate.now().plusDays(3), StatusFatura.ABERTA);

        faturaLembreteService.executarJob();
        var resultado2 = faturaLembreteService.executarJob();

        assertThat(resultado2.sent()).isZero();
        assertThat(resultado2.skipped()).isEqualTo(1);
        // Email enviado só uma vez no total
        verify(externalEmailSenderService, times(1)).sendHtml(anyString(), anyString(), anyString());
    }

    @Test
    void naoDeveEnviarSeOptOutDesativado() {
        usuario.setNotificacoesFaturaAtivo(false);
        usuarioRepository.save(usuario);
        criarFatura(LocalDate.now().plusDays(3), StatusFatura.ABERTA);

        var resultado = faturaLembreteService.executarJob();

        assertThat(resultado.processed()).isZero();
        verifyNoInteractions(externalEmailSenderService);
    }

    @Test
    void naoDeveEnviarParaFaturaPaga() {
        criarFatura(LocalDate.now().plusDays(3), StatusFatura.PAGA);

        var resultado = faturaLembreteService.executarJob();

        assertThat(resultado.processed()).isZero();
        verifyNoInteractions(externalEmailSenderService);
    }

    @Test
    void deveRegistrarErroQuandoEmailFalha() {
        criarFatura(LocalDate.now().plusDays(1), StatusFatura.ABERTA);
        doThrow(new RuntimeException("SMTP indisponível"))
                .when(externalEmailSenderService).sendHtml(anyString(), anyString(), anyString());

        var resultado = faturaLembreteService.executarJob();

        assertThat(resultado.errors()).isEqualTo(1);
        assertThat(resultado.sent()).isZero();

        var notificacao = notificacaoRepository.findAll().get(0);
        assertThat(notificacao.getStatus()).isEqualTo(StatusNotificacaoFatura.ERRO);
        assertThat(notificacao.getErro()).contains("SMTP indisponível");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FaturaEntity criarFatura(LocalDate dataVencimento, StatusFatura status) {
        FaturaEntity fatura = new FaturaEntity();
        fatura.setCartao(cartao);
        fatura.setUsuario(usuario);
        fatura.setMes(dataVencimento.getMonthValue());
        fatura.setAno(dataVencimento.getYear());
        fatura.setDataFechamento(dataVencimento.minusDays(10));
        fatura.setDataVencimento(dataVencimento);
        fatura.setValorTotal(new BigDecimal("500.00"));
        fatura.setValorPago(BigDecimal.ZERO);
        fatura.setStatus(status);
        fatura.setAtivo(true);
        return faturaRepository.save(fatura);
    }
}
