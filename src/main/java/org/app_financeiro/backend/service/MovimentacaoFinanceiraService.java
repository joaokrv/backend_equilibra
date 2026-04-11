package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.model.ResultadoMovimentacaoCartao;
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
 * Serviço responsável por orquestrar os impactos financeiros entre Contas, Cartões e Faturas.
 * Garante a integridade dos saldos e limites durante a criação, edição e exclusão de transações.
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
     * Processa o impacto financeiro em uma conta bancária baseado no status da transação.
     *
     * @param tipo tipo da transação (RECEITA/DESPESA)
     * @param status status atual da transação
     * @param contaId ID da conta afetada
     * @param valor valor da movimentação
     * @param usuarioId ID do usuário proprietário
     * @return entidade da conta com saldo atualizado
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
     * Processa uma despesa em cartão de crédito, consumindo limite e registrando na fatura.
     *
     * @param cartaoId ID do cartão utilizado
     * @param data data da transação
     * @param valor valor da despesa
     * @param usuarioId ID do usuário proprietário
     * @return record contendo o cartão e a fatura afetados
     */
    @Transactional
    public ResultadoMovimentacaoCartao processarDespesaCartao(Long cartaoId, LocalDate data,
                                                               BigDecimal valor, Long usuarioId) {
        CartaoEntity cartao = cartaoService.consumirLimite(cartaoId, valor, usuarioId);
        FaturaEntity fatura = faturaService.adicionarTransacao(cartao, data, valor);
        log.info("Despesa de R$ {} processada no cartão {}", valor, cartaoId);
        return new ResultadoMovimentacaoCartao(cartao, fatura);
    }

    /**
     * Processa um estorno ou crédito em cartão de crédito, liberando limite e ajustando a fatura.
     *
     * @param cartaoId ID do cartão utilizado
     * @param data data do estorno
     * @param valor valor do crédito
     * @param usuarioId ID do usuário proprietário
     * @return record contendo o cartão e a fatura afetados
     */
    @Transactional
    public ResultadoMovimentacaoCartao processarEstornoCartao(Long cartaoId, LocalDate data, BigDecimal valor, Long usuarioId) {
        CartaoEntity cartao = cartaoService.buscarCartaoValidado(cartaoId, usuarioId);
        FaturaEntity fatura = faturaService.registrarCredito(cartao, data, valor);
        log.info("Crédito/Estorno de R$ {} processado no cartão {}", valor, cartaoId);
        return new ResultadoMovimentacaoCartao(cartao, fatura);
    }

    /**
     * Reverte o efeito financeiro de uma transação ativa.
     * Utilizado para neutralizar o impacto antes de exclusões ou alterações de valores.
     *
     * @param transacao entidade da transação a ser revertida
     * @param usuarioId ID do usuário proprietário
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
        } else {
            log.warn("Nenhum efeito revertido para transação {} — conta e cartão/fatura ausentes", transacao.getId());
            return;
        }
        log.info("Efeito financeiro da transação {} revertido", transacao.getId());
    }
}
