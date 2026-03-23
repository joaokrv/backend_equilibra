package org.app_financeiro.backend.service;

import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Serviço responsável por encapsular a orquestração de impactos financeiros
 * envolvendo Contas, Cartões e Faturas.
 */
@Service
public class MovimentacaoFinanceiraService {

    private static final Logger log = LoggerFactory.getLogger(MovimentacaoFinanceiraService.class);

    private final ContaService contaService;
    private final CartaoService cartaoService;
    private final FaturaService faturaService;

    public MovimentacaoFinanceiraService(ContaService contaService,
                                         CartaoService cartaoService,
                                         FaturaService faturaService) {
        this.contaService = contaService;
        this.cartaoService = cartaoService;
        this.faturaService = faturaService;
    }

    /**
     * Aplica impacto financeiro em conta bancária.
     * Só impacta o saldo quando status = PAGO (DESPESA debita, RECEITA credita).
     * Sempre retorna a entidade da conta validada.
     */
    @Transactional
    public ContaEntity processarTransacaoConta(TipoTransacao tipo, StatusTransacao status,
                                               Long contaId, BigDecimal valor, Long usuarioId) {
        if (status == StatusTransacao.PAGO) {
            if (tipo == TipoTransacao.DESPESA) {
                log.info("Processando débito de R$ {} na conta {} (DESPESA+PAGO)", valor, contaId);
                return contaService.debitarSaldo(contaId, valor, usuarioId);
            } else {
                log.info("Processando crédito de R$ {} na conta {} (RECEITA+PAGO)", valor, contaId);
                return contaService.creditarSaldo(contaId, valor, usuarioId);
            }
        }
        return contaService.buscarContaValidada(contaId, usuarioId);
    }

    /**
     * Aplica impacto financeiro de despesa em cartão de crédito.
     * Consome o limite do cartão e adiciona a transação na fatura correspondente.
     */
    @Transactional
    public ResultadoMovimentacaoCartao processarDespesaCartao(Long cartaoId, LocalDate data,
                                                              BigDecimal valor, Long usuarioId) {
        CartaoEntity cartao = cartaoService.consumirLimite(cartaoId, valor, usuarioId);
        FaturaEntity fatura = faturaService.adicionarTransacao(cartao, data, valor);
        log.info("Despesa de R$ {} processada no cartão {}. Limite consumido + fatura atualizada", valor, cartaoId);
        return new ResultadoMovimentacaoCartao(cartao, fatura);
    }

    /**
     * Registra receita (estorno/cashback) vinculada a um cartão.
     * Reduz o valor total da fatura.
     */
    @Transactional
    public ResultadoMovimentacaoCartao processarEstornoCartao(Long cartaoId, LocalDate data, BigDecimal valor, Long usuarioId) {
        CartaoEntity cartao = cartaoService.buscarCartaoValidado(cartaoId, usuarioId);
        FaturaEntity fatura = faturaService.registrarCredito(cartao, data, valor);
        return new ResultadoMovimentacaoCartao(cartao, fatura);
    }

    /**
     * Desfaz o efeito financeiro de uma transação.
     * Usado antes de atualizar ou deletar transações.
     */
    @Transactional
    public void desfazerEfeitoFinanceiro(TransacaoEntity transacao, Long usuarioId) {
        if (transacao.getConta() != null && transacao.getStatus() == StatusTransacao.PAGO) {
            if (transacao.getTipo() == TipoTransacao.DESPESA) {
                contaService.creditarSaldo(transacao.getConta().getId(), transacao.getValor(), usuarioId);
            } else {
                contaService.debitarSaldo(transacao.getConta().getId(), transacao.getValor(), usuarioId);
            }
        } else if (transacao.getCartao() != null && transacao.getFatura() != null) {
            if (transacao.getTipo() == TipoTransacao.DESPESA) {
                faturaService.removerTransacaoPorFatura(transacao.getFatura(), transacao.getValor());
            } else {
                faturaService.adicionarTransacaoPorFatura(transacao.getFatura(), transacao.getValor());
            }
        }
        log.info("Efeito financeiro da transação {} revertido", transacao.getId());
    }
}
