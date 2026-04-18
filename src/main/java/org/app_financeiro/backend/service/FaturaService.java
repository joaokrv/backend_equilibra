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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
        // 1. Descobre a qual mês/ano essa transação pertence (considerando fechamento)
        LocalDate dataReferencia = calcularDataReferenciaFatura(dataTransacao, cartao.getDiaFechamento());
        int mes = dataReferencia.getMonthValue();
        int ano = dataReferencia.getYear();
        
        // 2. Busca a fatura desse mês/ano. Se não existir, cria dinamicamente ("Lazy Creation")
        FaturaEntity fatura = faturaRepository.findByCartaoIdAndMesAndAno(cartao.getId(), mes, ano)
                .orElseGet(() -> criarNovaFatura(cartao, mes, ano));
                
        // 3. Adiciona o valor à fatura e salva
        fatura.setValorTotal(fatura.getValorTotal().add(valor));
        log.info("Transação adicionada à fatura {}/{} do cartão {}. Valor: R$ {}", mes, ano, cartao.getId(), valor);
        return faturaRepository.save(fatura);
    }

    @Transactional
    public void removerTransacaoPorFatura(FaturaEntity fatura, BigDecimal valor) {
        BigDecimal novoValorTotal = fatura.getValorTotal().subtract(valor);
        if (novoValorTotal.compareTo(BigDecimal.ZERO) < 0) {
            novoValorTotal = BigDecimal.ZERO;
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
        if (novoValorTotal.compareTo(BigDecimal.ZERO) < 0) {
            novoValorTotal = BigDecimal.ZERO;
        }
        fatura.setValorTotal(novoValorTotal);
        return faturaRepository.save(fatura);
    }

    @Transactional
    public FaturaResponseDTO pagarFatura(Long faturaId, Long usuarioId, PagarFaturaRequestDTO dto) {
        FaturaEntity fatura = buscarPorId(faturaId, usuarioId);

        if (fatura.getStatus() == StatusFatura.PAGA) {
            log.warn("Tentativa de pagar fatura {} que já está PAGA", faturaId);
            throw new RegraDeNegocioException("Esta fatura já está totalmente paga.");
        }

        // Verifica se o usuário não está tentando pagar mais do que deve
        BigDecimal dividaRestante = fatura.getValorTotal().subtract(fatura.getValorPago());
        if (dto.valorPago().compareTo(dividaRestante) > 0) {
            log.warn("Pagamento de R$ {} excede dívida restante de R$ {} na fatura {}", dto.valorPago(), dividaRestante, faturaId);
            throw new RegraDeNegocioException("O valor do pagamento não pode ser maior que o restante da fatura (R$ " + dividaRestante + ").");
        }
        
        // 1. Debita o valor do pagamento da Conta selecionada
        contaService.debitarSaldo(dto.contaId(), dto.valorPago(), usuarioId);
        
        // 2. Registra o pagamento na fatura
        fatura.setValorPago(fatura.getValorPago().add(dto.valorPago()));
        
        // 3. Se o que foi pago for Maior ou Igual à divida total, quita a fatura
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
        boolean algumaFoiAtualizada = false;

        for (FaturaEntity fatura : faturas) {
            if (fatura.getStatus() == StatusFatura.ABERTA && hoje.isAfter(fatura.getDataFechamento())) {
                fatura.setStatus(StatusFatura.FECHADA);
                algumaFoiAtualizada = true;
            }
            if (fatura.getStatus() == StatusFatura.FECHADA && hoje.isAfter(fatura.getDataVencimento())) {
                fatura.setStatus(StatusFatura.ATRASADA);
                algumaFoiAtualizada = true;
            }
        }

        if (algumaFoiAtualizada) {
            faturaRepository.saveAll(faturas);
            log.debug("Ghost closing: {} fatura(s) com status atualizado.", faturas.size());
        }
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
            return dataTransacao.plusMonths(1); // Fatura virou, cai no próximo mês
        }
        return dataTransacao; // Cai no mês atual da transação
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
        
        // Data de Fechamento: No mês da fatura, no dia estabelecido pelo cartão
        LocalDate dataFechamento = calcularDataComLimite(ano, mes, cartao.getDiaFechamento());
        
        // Lógica do Vencimento (Verifica se vence no mesmo mês ou no mês seguinte)
        int mesVencimento = mes;
        int anoVencimento = ano;
        
        if (cartao.getDiaVencimento() < cartao.getDiaFechamento()) {
            mesVencimento++; // Joga para o próximo mês
            if (mesVencimento > 12) { 
                mesVencimento = 1; 
                anoVencimento++; 
            } // Tratamento de Virada de Ano
        }
        
        LocalDate dataVencimento = calcularDataComLimite(anoVencimento, mesVencimento, cartao.getDiaVencimento());
        
        nova.setDataFechamento(dataFechamento);
        nova.setDataVencimento(dataVencimento);
        nova.setAtivo(true);
        return nova;
    }

    /** Limita o dia ao último dia válido do mês, evitando exceções como "31 de Fevereiro". */
    private LocalDate calcularDataComLimite(int ano, int mes, int diaDesejado) {
        int maxDiasNoMes = YearMonth.of(ano, mes).lengthOfMonth();
        int diaReal = Math.min(diaDesejado, maxDiasNoMes); 
        return LocalDate.of(ano, mes, diaReal);
    }
}