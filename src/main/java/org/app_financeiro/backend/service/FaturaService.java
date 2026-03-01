package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.PagarFaturaRequestDTO;
import org.app_financeiro.backend.dto.response.FaturaResponseDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.FaturaRepository;
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

    private final UsuarioService usuarioService;
    private final CartaoService cartaoService;
    private final ContaService contaService;
    private final FaturaRepository faturaRepository;

    public FaturaService(UsuarioService usuarioService, CartaoService cartaoService, ContaService contaService, FaturaRepository faturaRepository) {
        this.usuarioService = usuarioService;
        this.cartaoService = cartaoService;
        this.contaService = contaService;
        this.faturaRepository = faturaRepository;
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
        return faturaRepository.save(fatura);
    }

    /**
     * Remove o valor de uma transação da fatura correspondente.
     * Usado quando uma transação é deletada ou tem seu valor atualizado (estorno do valor antigo).
     * Subtrai o valor do valorTotal da fatura e salva.
     *
     * @param cartao Cartão associado à transação
     * @param dataTransacao Data da transação original
     * @param valor Valor a ser subtraído
     */
    @Transactional
    public void removerTransacao(CartaoEntity cartao, LocalDate dataTransacao, BigDecimal valor) {
        LocalDate dataReferencia = calcularDataReferenciaFatura(dataTransacao, cartao.getDiaFechamento());
        int mes = dataReferencia.getMonthValue();
        int ano = dataReferencia.getYear();

        FaturaEntity fatura = faturaRepository.findByCartaoIdAndMesAndAno(cartao.getId(), mes, ano)
                .orElseThrow(() -> new RegraDeNegocioException("Fatura não encontrada para o mês da transação que está sendo removida."));

        // Proteção contra fatura negativa
        BigDecimal novoValorTotal = fatura.getValorTotal().subtract(valor);
        if (novoValorTotal.compareTo(BigDecimal.ZERO) < 0) {
            novoValorTotal = BigDecimal.ZERO;
        }

        fatura.setValorTotal(novoValorTotal);
        faturaRepository.save(fatura);
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
            throw new RegraDeNegocioException("Esta fatura já está totalmente paga.");
        }

        // Verifica se o usuário não está tentando pagar mais do que deve
        BigDecimal dividaRestante = fatura.getValorTotal().subtract(fatura.getValorPago());
        if (dto.getValorPago().compareTo(dividaRestante) > 0) {
            throw new RegraDeNegocioException("O valor do pagamento não pode ser maior que o restante da fatura (R$ " + dividaRestante + ").");
        }
        
        // 1. Debita o valor do pagamento da Conta selecionada
        contaService.debitarSaldo(dto.getContaId(), dto.getValorPago(), usuarioId);
        
        // 2. Registra o pagamento na fatura
        fatura.setValorPago(fatura.getValorPago().add(dto.getValorPago()));
        
        // 3. Se o que foi pago for Maior ou Igual à divida total, quita a fatura
        if (fatura.getValorPago().compareTo(fatura.getValorTotal()) >= 0) {
            fatura.setStatus(StatusFatura.PAGA);
        }
        
        faturaRepository.save(fatura);
        return new FaturaResponseDTO(fatura);
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
        }

        return faturas.stream().map(FaturaResponseDTO::new).toList();
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