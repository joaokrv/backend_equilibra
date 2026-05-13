package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.model.ResultadoMovimentacaoCartao;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovimentacaoFinanceiraServiceTest {

    @Mock
    private ContaService contaService;

    @Mock
    private CartaoService cartaoService;

    @Mock
    private FaturaService faturaService;

    @InjectMocks
    private MovimentacaoFinanceiraService movimentacaoFinanceiraService;

    private ContaEntity contaPadrao;
    private CartaoEntity cartaoPadrao;
    private FaturaEntity faturaPadrao;

    @BeforeEach
    void setUp() {
        contaPadrao = new ContaEntity();
        contaPadrao.setId(10L);

        cartaoPadrao = new CartaoEntity();
        cartaoPadrao.setId(20L);

        faturaPadrao = new FaturaEntity();
        faturaPadrao.setId(100L);
    }

    @Test
    void deveProcessarTransacaoContaDespesaPaga() {
        when(contaService.debitarSaldo(10L, new BigDecimal("100.00"), 1L)).thenReturn(contaPadrao);

        ContaEntity result = movimentacaoFinanceiraService.processarTransacaoConta(
                TipoTransacao.DESPESA, StatusTransacao.PAGO, 10L, new BigDecimal("100.00"), 1L);

        assertThat(result).isEqualTo(contaPadrao);
        verify(contaService).debitarSaldo(10L, new BigDecimal("100.00"), 1L);
    }

    @Test
    void deveProcessarTransacaoContaReceitaPaga() {
        when(contaService.creditarSaldo(10L, new BigDecimal("500.00"), 1L)).thenReturn(contaPadrao);

        ContaEntity result = movimentacaoFinanceiraService.processarTransacaoConta(
                TipoTransacao.RECEITA, StatusTransacao.PAGO, 10L, new BigDecimal("500.00"), 1L);

        assertThat(result).isEqualTo(contaPadrao);
        verify(contaService).creditarSaldo(10L, new BigDecimal("500.00"), 1L);
    }

    @Test
    void naoDeveImpactarSaldoSeStatusForPendente() {
        when(contaService.buscarContaValidada(10L, 1L)).thenReturn(contaPadrao);

        movimentacaoFinanceiraService.processarTransacaoConta(
                TipoTransacao.DESPESA, StatusTransacao.PENDENTE, 10L, new BigDecimal("100.00"), 1L);

        verify(contaService, never()).debitarSaldo(any(), any(), any());
        verify(contaService, never()).creditarSaldo(any(), any(), any());
        verify(contaService).buscarContaValidada(10L, 1L);
    }

    @Test
    void deveProcessarDespesaCartao() {
        when(cartaoService.consumirLimite(eq(20L), eq(new BigDecimal("150.00")), eq(1L))).thenReturn(cartaoPadrao);
        when(faturaService.adicionarTransacao(eq(cartaoPadrao), any(), eq(new BigDecimal("150.00")))).thenReturn(faturaPadrao);

        ResultadoMovimentacaoCartao result = movimentacaoFinanceiraService.processarDespesaCartao(
                20L, LocalDate.now(), new BigDecimal("150.00"), 1L);

        assertThat(result.cartao()).isEqualTo(cartaoPadrao);
        assertThat(result.fatura()).isEqualTo(faturaPadrao);
        verify(cartaoService).consumirLimite(any(), any(), any());
        verify(faturaService).adicionarTransacao(any(), any(), any());
    }

    @Test
    void deveProcessarEstornoCartao() {
        when(cartaoService.obterCartaoComBloqueioExclusivo(20L, 1L)).thenReturn(cartaoPadrao);
        when(faturaService.registrarCredito(eq(cartaoPadrao), any(), eq(new BigDecimal("50.00")))).thenReturn(faturaPadrao);

        ResultadoMovimentacaoCartao result = movimentacaoFinanceiraService.processarEstornoCartao(
                20L, LocalDate.now(), new BigDecimal("50.00"), 1L);

        assertThat(result.cartao()).isEqualTo(cartaoPadrao);
        verify(faturaService).registrarCredito(any(), any(), any());
    }

    @Test
    void deveDesfazerEfeitoFinanceiroContaDespesa() {
        TransacaoEntity t = new TransacaoEntity();
        t.setConta(contaPadrao);
        t.setStatus(StatusTransacao.PAGO);
        t.setTipo(TipoTransacao.DESPESA);
        t.setValor(new BigDecimal("100.00"));

        movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(t, 1L);

        verify(contaService).creditarSaldo(10L, new BigDecimal("100.00"), 1L);
    }

    @Test
    void deveDesfazerEfeitoFinanceiroCartaoDespesa() {
        TransacaoEntity t = new TransacaoEntity();
        t.setCartao(cartaoPadrao);
        t.setFatura(faturaPadrao);
        t.setTipo(TipoTransacao.DESPESA);
        t.setValor(new BigDecimal("150.00"));

        movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(t, 1L);

        verify(faturaService).removerTransacaoPorFatura(faturaPadrao, new BigDecimal("150.00"));
    }
}
