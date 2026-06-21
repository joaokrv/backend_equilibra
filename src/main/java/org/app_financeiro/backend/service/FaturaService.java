package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.PagarFaturaRequestDTO;
import org.app_financeiro.backend.dto.response.FaturaResponseDTO;
import org.app_financeiro.backend.mapper.FaturaMapper;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.FaturaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.app_financeiro.backend.util.FaturaDateUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Gerencia faturas: criação lazy, ciclo de vida, ghost closing e pagamentos. */
@Service
public class FaturaService {

    private static final Logger log = LoggerFactory.getLogger(FaturaService.class);

    private final UsuarioService usuarioService;
    private final CartaoService cartaoService;
    private final ContaService contaService;
    private final FaturaRepository faturaRepository;
    private final FaturaMapper faturaMapper;

    public FaturaService(UsuarioService usuarioService, CartaoService cartaoService, ContaService contaService, FaturaRepository faturaRepository, FaturaMapper faturaMapper) {
        this.usuarioService = usuarioService;
        this.cartaoService = cartaoService;
        this.contaService = contaService;
        this.faturaRepository = faturaRepository;
        this.faturaMapper = faturaMapper;
    }

    @Transactional
    public FaturaEntity adicionarTransacao(CartaoEntity cartao, LocalDate dataTransacao, BigDecimal valor) {
        LocalDate dataReferencia = calcularDataReferenciaFatura(dataTransacao, cartao.getDiaFechamento());
        int mes = dataReferencia.getMonthValue();
        int ano = dataReferencia.getYear();
        
        FaturaEntity fatura = faturaRepository.findByCartaoIdAndMesAndAno(cartao.getId(), mes, ano)
                .orElseGet(() -> criarNovaFatura(cartao, mes, ano));
                
        fatura.setValorTotal(fatura.getValorTotal().add(valor));
        log.info("Transação adicionada à fatura {}/{} do cartão {}. Valor: R$ {}", mes, ano, cartao.getId(), valor);
        return faturaRepository.save(fatura);
    }

    @Transactional
    public void removerTransacaoPorFatura(FaturaEntity fatura, BigDecimal valor) {
        BigDecimal novoValorTotal = fatura.getValorTotal().subtract(valor);

        if (novoValorTotal.compareTo(fatura.getValorPago()) < 0) {
            throw new RegraDeNegocioException(
                "error.fatura.reducao_abaixo_do_pago",
                "Não é possível remover esta transação: o valor da fatura ficaria menor que o total já pago.");
        }

        fatura.setValorTotal(novoValorTotal);
        faturaRepository.save(fatura);
    }

    @Transactional
    public void adicionarTransacaoPorFatura(FaturaEntity fatura, BigDecimal valor) {
        fatura.setValorTotal(fatura.getValorTotal().add(valor));
        faturaRepository.save(fatura);
    }

    @Transactional
    public FaturaEntity registrarCredito(CartaoEntity cartao, LocalDate dataTransacao, BigDecimal valor) {
        LocalDate dataReferencia = calcularDataReferenciaFatura(dataTransacao, cartao.getDiaFechamento());
        int mes = dataReferencia.getMonthValue();
        int ano = dataReferencia.getYear();

        FaturaEntity fatura = faturaRepository.findByCartaoIdAndMesAndAno(cartao.getId(), mes, ano)
                .orElseGet(() -> criarNovaFatura(cartao, mes, ano));

        BigDecimal novoValorTotal = fatura.getValorTotal().subtract(valor);
        if (novoValorTotal.compareTo(fatura.getValorPago()) < 0) {
            throw new RegraDeNegocioException("error.fatura.estorno_excede",
                    "O valor do estorno excede o saldo devedor da fatura deste período.");
        }
        fatura.setValorTotal(novoValorTotal);
        return faturaRepository.save(fatura);
    }

    @Transactional
    public FaturaResponseDTO pagarFatura(Long faturaId, Long usuarioId, PagarFaturaRequestDTO dto) {
        FaturaEntity fatura = buscarPorId(faturaId, usuarioId);
        atualizarStatusVencidas(List.of(fatura));

        if (fatura.getStatus() == StatusFatura.PAGA) {
            log.warn("Tentativa de pagar fatura {} que já está PAGA", faturaId);
            throw new RegraDeNegocioException("error.fatura.ja_paga", "Esta fatura já está totalmente paga.");
        }

        BigDecimal dividaRestante = fatura.getValorTotal().subtract(fatura.getValorPago());
        if (dto.valorPago().compareTo(dividaRestante) > 0) {
            log.warn("Pagamento de R$ {} excede dívida restante de R$ {} na fatura {}", dto.valorPago(), dividaRestante, faturaId);
            throw new RegraDeNegocioException("error.fatura.pagamento_excede", "O valor do pagamento não pode ser maior que o restante da fatura (R$ " + dividaRestante + ").", dividaRestante);
        }
        
        contaService.debitarSaldo(dto.contaId(), dto.valorPago(), usuarioId);
        
        fatura.setValorPago(fatura.getValorPago().add(dto.valorPago()));
        
        if (fatura.getValorPago().compareTo(fatura.getValorTotal()) >= 0) {
            fatura.setStatus(StatusFatura.PAGA);
        }
        
        faturaRepository.save(fatura);
        log.info("Fatura {} paga com R$ {}. Status: {}", faturaId, dto.valorPago(), fatura.getStatus());
        return faturaMapper.toResponse(fatura);
    }

    @Transactional
    public List<FaturaResponseDTO> listarFaturasPorCartao(Long cartaoId, Long usuarioId) {
        cartaoService.buscarPorId(cartaoId, usuarioId);
        List<FaturaEntity> faturas = faturaRepository.findByCartaoId(cartaoId);
        atualizarStatusVencidas(faturas);
        return faturas.stream().map(faturaMapper::toResponse).toList();
    }

    /** Ghost closing: atualiza status de faturas vencidas dentro da mesma transação do GET. */
    private void atualizarStatusVencidas(List<FaturaEntity> faturas) {
        LocalDate hoje = LocalDate.now();
        List<FaturaEntity> alteradas = new ArrayList<>();

        for (FaturaEntity fatura : faturas) {
            boolean mudou = false;
            if (fatura.getStatus() == StatusFatura.ABERTA && hoje.isAfter(fatura.getDataFechamento())) {
                fatura.setStatus(StatusFatura.FECHADA);
                mudou = true;
            }
            if (fatura.getStatus() == StatusFatura.FECHADA && hoje.isAfter(fatura.getDataVencimento())) {
                fatura.setStatus(StatusFatura.ATRASADA);
                mudou = true;
            }
            if (mudou) {
                alteradas.add(fatura);
            }
        }

        if (!alteradas.isEmpty()) {
            faturaRepository.saveAll(alteradas);
            log.debug("Ghost closing: {} fatura(s) com status atualizado.", alteradas.size());
        }
    }

    @Transactional
    public FaturaResponseDTO buscarFaturaComDetalhe(Long faturaId, Long usuarioId) {
        FaturaEntity fatura = buscarPorId(faturaId, usuarioId);
        atualizarStatusVencidas(List.of(fatura));
        return faturaMapper.toResponse(fatura);
    }

    public FaturaEntity buscarPorId(Long faturaId, Long usuarioId) {
        FaturaEntity fatura = faturaRepository.findById(faturaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fatura não encontrada"));

        if (!fatura.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Acesso negado à fatura.");
        }

        return fatura;
    }

    private LocalDate calcularDataReferenciaFatura(LocalDate dataTransacao, int diaFechamento) {
        if (dataTransacao.getDayOfMonth() >= diaFechamento) {
            return dataTransacao.plusMonths(1);
        }
        return dataTransacao;
    }

    private FaturaEntity criarNovaFatura(CartaoEntity cartao, int mes, int ano) {
        FaturaEntity nova = new FaturaEntity();
        nova.setCartao(cartao);
        nova.setUsuario(cartao.getUsuario());
        nova.setMes(mes);
        nova.setAno(ano);
        nova.setValorTotal(BigDecimal.ZERO);
        nova.setValorPago(BigDecimal.ZERO);
        nova.setStatus(StatusFatura.ABERTA);
        
        LocalDate dataFechamento = FaturaDateUtil.calcularDataFechamento(ano, mes, cartao.getDiaFechamento());
        LocalDate dataVencimento = FaturaDateUtil.calcularDataVencimento(ano, mes, cartao.getDiaFechamento(), cartao.getDiaVencimento());
        
        nova.setDataFechamento(dataFechamento);
        nova.setDataVencimento(dataVencimento);
        nova.setAtivo(true);
        return nova;
    }

}