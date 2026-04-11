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

/**
 * Serviço responsável por toda a engenharia financeira do sistema de Faturas.
 * Lida com a criação automática de faturas, cálculos de meses com base nos
 * dias de vencimento/fechamento dos cartões e pagamentos integrados com o ContaService.
 */
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

    /**
     * Adiciona o valor de uma transação à fatura correspondente.
     * Se a fatura não existir para o mês/ano da transação, cria uma nova.
     * Atualiza o valorTotal da fatura e salva.
     *
     * @param cartao Cartão associado à transação
     * @param dataTransacao Data em que a transação ocorreu
     * @param valor Valor da transação a ser adicionado
     * @return A FaturaEntity atualizada/criada
     */
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

    /**
     * Remove o valor de uma transação da fatura diretamente a partir da entidade Fatura.
     * Subtrai o valor do valorTotal da fatura e salva.
     *
     * @param fatura Entidade Fatura que sofrerá o decréscimo
     * @param valor Valor a ser subtraído
     */
    @Transactional
    public void removerTransacaoPorFatura(FaturaEntity fatura, BigDecimal valor) {
        BigDecimal novoValorTotal = fatura.getValorTotal().subtract(valor);
        if (novoValorTotal.compareTo(BigDecimal.ZERO) < 0) {
            novoValorTotal = BigDecimal.ZERO;
        }

        fatura.setValorTotal(novoValorTotal);
        faturaRepository.save(fatura);
    }

    /**
     * Adiciona o valor de uma transação à fatura diretamente a partir da entidade Fatura.
     * Usado ao reverter impacto financeiro (ex: desfazer exclusão de receita/estorno).
     *
     * @param fatura Entidade Fatura que sofrerá o acréscimo
     * @param valor Valor a ser adicionado
     */
    @Transactional
    public void adicionarTransacaoPorFatura(FaturaEntity fatura, BigDecimal valor) {
        fatura.setValorTotal(fatura.getValorTotal().add(valor));
        faturaRepository.save(fatura);
    }

    /**
     * Registra um estorno ou cashback na fatura (Receita vinculada ao cartão).
     * Subtrai o valor do valorTotal da fatura e salva.
     */
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

    /**
     * Paga uma fatura existente, alterando seu status para PAGA se for integral.
     * Debita o valor pago da conta informada.
     * Valida se a fatura pertence ao usuário.
     *
     * @param faturaId ID da fatura
     * @param usuarioId ID do usuário
     * @param dto Contém o id da conta de débito e o valor a ser pago
     * @return Entidade da fatura atualizada
     */
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

    /**
     * Lista todas as faturas de um cartão específico do usuário.
     * Realiza um "Fechamento Fantasma" atualizando status de faturas vencidas antes de retornar.
     *
     * @param cartaoId ID do cartão
     * @param usuarioId ID do usuário
     * @return Lista de faturas do cartão
     */
    @Transactional
    public List<FaturaResponseDTO> listarFaturasPorCartao(Long cartaoId, Long usuarioId) {
        cartaoService.buscarPorId(cartaoId, usuarioId);
        List<FaturaEntity> faturas = faturaRepository.findByCartaoId(cartaoId);
        atualizarStatusVencidas(faturas);
        return faturas.stream().map(faturaMapper::toResponse).toList();
    }

    /**
     * Atualiza em memória (e persiste se necessário) os status de faturas que ultrapassaram
     * suas datas de fechamento ou vencimento — o chamado "Fechamento Fantasma".
     * Executado dentro da mesma transação do método que lista as faturas.
     */
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

    /**
     * Busca uma fatura por ID validando que pertence ao usuário.
     * Método de uso interno, chamado por pagarFatura e pelo TransacaoService.
     *
     * @param faturaId  ID da fatura
     * @param usuarioId ID do usuário autenticado
     * @return FaturaEntity correspondente
     * @throws RecursoNaoEncontradoException se a fatura não existir ou não pertencer ao usuário
     */
    public FaturaEntity buscarPorId(Long faturaId, Long usuarioId) {
        FaturaEntity fatura = faturaRepository.findById(faturaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fatura não encontrada"));

        if (!fatura.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Acesso negado à fatura.");
        }

        return fatura;
    }

    /**
     * Calcula o mês de referência de uma transação com base no fechamento do cartão.
     */
    private LocalDate calcularDataReferenciaFatura(LocalDate dataTransacao, int diaFechamento) {
        if (dataTransacao.getDayOfMonth() >= diaFechamento) {
            return dataTransacao.plusMonths(1); // Fatura virou, cai no próximo mês
        }
        return dataTransacao; // Cai no mês atual da transação
    }

    /**
     * Cria uma fatura do zero e calcula as datas exatas de vencimento e fechamento
     * lidando com anos bissextos e fim de mês.
     */
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

    /**
     * Utilitário para evitar exceções como "31 de Fevereiro".
     * Se o dia desejado for maior que o máximo daquele mês, ele usa o último dia válido (ex: 28 ou 29).
     */
    private LocalDate calcularDataComLimite(int ano, int mes, int diaDesejado) {
        int maxDiasNoMes = YearMonth.of(ano, mes).lengthOfMonth();
        int diaReal = Math.min(diaDesejado, maxDiasNoMes); 
        return LocalDate.of(ano, mes, diaReal);
    }
}