package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.CartaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CartaoResponseDTO;
import org.app_financeiro.backend.mapper.CartaoMapper;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.LimiteInsuficienteException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.CartaoRepository;
import org.app_financeiro.backend.repository.FaturaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.app_financeiro.backend.dto.projections.DividaCartaoProjection;

/**
 * Serviço responsável pelo gerenciamento de Cartões de Crédito.
 *
 * Controla o ciclo de vida dos cartões (criação, consulta, soft delete) e
 * calcula o limite disponível em tempo real com base nas faturas pendentes.
 *
 * Regra de cálculo do limite disponível:
 *   Limite Disponível = Limite Total - soma(valorTotal - valorPago) das faturas NÃO PAGAS
 */
@Service
public class CartaoService {

    private static final Logger log = LoggerFactory.getLogger(CartaoService.class);

    private final CartaoRepository cartaoRepository;
    private final FaturaRepository faturaRepository;
    private final UsuarioService usuarioService;
    private final CartaoMapper cartaoMapper;
    private final ContaRepository contaRepository;

    public CartaoService(CartaoRepository cartaoRepository, FaturaRepository faturaRepository, UsuarioService usuarioService, CartaoMapper cartaoMapper, ContaRepository contaRepository) {
        this.cartaoRepository = cartaoRepository;
        this.faturaRepository = faturaRepository;
        this.usuarioService = usuarioService;
        this.cartaoMapper = cartaoMapper;
        this.contaRepository = contaRepository;
    }

    /**
     * Cria um novo cartão de crédito para o usuário.
     * O limite disponível inicial é 100% do limite total (sem faturas pendentes).
     *
     * @param dto       Dados do cartão (nome, limite, dia fechamento, dia vencimento)
     * @param usuarioId ID do usuário autenticado
     * @return CartaoResponseDTO com os dados salvos e limite disponível
     * @throws RecursoNaoEncontradoException se o usuário não existir ou estiver inativo
     */
    @Transactional
    public CartaoResponseDTO criarCartao(CartaoRegistroRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        
        CartaoEntity cartao = new CartaoEntity();
        cartao.setNome(dto.nome());
        cartao.setLimite(dto.limite());
        cartao.setDiaFechamento(dto.diaFechamento());
        cartao.setDiaVencimento(dto.diaVencimento());
        cartao.setUsuario(usuario);
        cartao.setAtivo(true);

        if (dto.contaId() != null) {
            ContaEntity conta = contaRepository.findById(dto.contaId())
                    .filter(c -> c.getUsuario().getId().equals(usuarioId))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada ou não pertence ao usuário"));
            cartao.setConta(conta);
        }
        
        CartaoEntity cartaoSalvo = cartaoRepository.save(cartao);
        
        log.info("Cartão criado: id={}, nome='{}', usuarioId={}", cartaoSalvo.getId(), cartaoSalvo.getNome(), usuarioId);
        // Limite disponível inicial é 100% do limite total
        return cartaoMapper.toResponse(cartaoSalvo, cartaoSalvo.getLimite()); 
    }

    /**
     * Busca um cartão específico por ID e calcula seu limite disponível atual.
     *
     * @param cartaoId  ID do cartão
     * @param usuarioId ID do dono do cartão
     * @return CartaoResponseDTO com dados do cartão e limite disponível calculado
     * @throws RecursoNaoEncontradoException se o cartão não existir ou não pertencer ao usuário
     */
    public CartaoResponseDTO buscarPorId(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = buscarCartaoValidado(cartaoId, usuarioId);
        BigDecimal limiteDisponivel = calcularLimiteDisponivel(cartao);
        return cartaoMapper.toResponse(cartao, limiteDisponivel);
    }

    /**
     * Calcula o limite disponível de um cartão subtraindo as dívidas de faturas não pagas.
     * Limite Disponível = Limite Total - Soma(valorTotal - valorPago) de faturas não PAGAS.
     *
     * @param cartao Entidade do cartão já validada
     * @return Limite disponível atual do cartão
     */
    public BigDecimal calcularLimiteDisponivel(CartaoEntity cartao) {
        List<FaturaEntity> faturasPendentes = faturaRepository.findByCartaoIdAndStatusNot(cartao.getId(), StatusFatura.PAGA);

        BigDecimal somaDividas = BigDecimal.ZERO;
        for (FaturaEntity fatura : faturasPendentes) {
            BigDecimal total = fatura.getValorTotal() != null ? fatura.getValorTotal() : BigDecimal.ZERO;
            BigDecimal pago  = fatura.getValorPago()  != null ? fatura.getValorPago()  : BigDecimal.ZERO;
            somaDividas = somaDividas.add(total.subtract(pago));
        }

        return cartao.getLimite().subtract(somaDividas);
    }

    /**
     * Lista todos os cartões ativos do usuário, calculando o limite disponível de cada um em tempo real.
     *
     * @param usuarioId ID do usuário autenticado
     * @return Lista de CartaoResponseDTO (pode ser vazia)
     */
    @Transactional(readOnly = true)
    public List<CartaoResponseDTO> buscarTodosDoUsuario(Long usuarioId) {
        List<CartaoEntity> cartoes = cartaoRepository.findByUsuarioId(usuarioId);

        // Fetch all debts in a single query to avoid N+1
        List<DividaCartaoProjection> dividas = faturaRepository.somarDividasPorCartoes(usuarioId, StatusFatura.PAGA);

        // Create a map for quick access O(1)
        Map<Long, BigDecimal> mapaDividas = dividas.stream()
                .filter(d -> d.totalDivida() != null)
                .collect(Collectors.toMap(DividaCartaoProjection::cartaoId, DividaCartaoProjection::totalDivida));

        return cartoes.stream()
                .map(cartao -> {
                    BigDecimal divida = mapaDividas.getOrDefault(cartao.getId(), BigDecimal.ZERO);
                    BigDecimal limiteDisponivel = cartao.getLimite().subtract(divida);
                    return cartaoMapper.toResponse(cartao, limiteDisponivel);
                })
                .toList();
    }

    /**
     * Atualiza os dados de um cartão existente.
     *
     * Regra importante:
     * - O novo limite não pode ser menor que o valor já utilizado em faturas não pagas.
     */
    @Transactional
    public CartaoResponseDTO atualizarCartao(Long cartaoId, CartaoRegistroRequestDTO dto, Long usuarioId) {
        CartaoEntity cartao = buscarCartaoValidado(cartaoId, usuarioId);

        BigDecimal limiteDisponivelAtual = calcularLimiteDisponivel(cartao);
        BigDecimal limiteUtilizado = cartao.getLimite().subtract(limiteDisponivelAtual);

        if (dto.limite().compareTo(limiteUtilizado) < 0) {
            throw new RegraDeNegocioException(
                    "O novo limite não pode ser menor que o valor já utilizado no cartão."
            );
        }

        cartao.setNome(dto.nome());
        cartao.setLimite(dto.limite());
        cartao.setDiaFechamento(dto.diaFechamento());
        cartao.setDiaVencimento(dto.diaVencimento());
        cartao.setBandeira(dto.bandeira());

        if (dto.contaId() != null) {
            ContaEntity conta = contaRepository.findById(dto.contaId())
                    .filter(c -> c.getUsuario().getId().equals(usuarioId))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada ou não pertence ao usuário"));
            cartao.setConta(conta);
        } else {
            cartao.setConta(null);
        }

        CartaoEntity cartaoAtualizado = cartaoRepository.save(cartao);
        BigDecimal limiteDisponivelAtualizado = calcularLimiteDisponivel(cartaoAtualizado);

        log.info("Cartão atualizado: id={}, usuarioId={}", cartaoId, usuarioId);
        return cartaoMapper.toResponse(cartaoAtualizado, limiteDisponivelAtualizado);
    }

    /**
     * Desativa (soft delete) um cartão de crédito.
     * Bloqueia a operação se o cartão possuir faturas não pagas (ABERTA, FECHADA ou ATRASADA).
     *
     * @param cartaoId  ID do cartão a desativar
     * @param usuarioId ID do dono do cartão
     * @throws RecursoNaoEncontradoException se o cartão não existir ou não pertencer ao usuário
     * @throws RegraDeNegocioException       se o cartão possuir faturas pendentes
     */
    @Transactional
    public void deletarCartao(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = buscarCartaoValidado(cartaoId, usuarioId);
        
        boolean temFaturasPendentes = faturaRepository.existsByCartaoIdAndStatusNot(cartaoId, StatusFatura.PAGA);
        if (temFaturasPendentes) {
            log.warn("Tentativa de deletar cartão com faturas pendentes: cartaoId={}", cartaoId);
            throw new RegraDeNegocioException("Não é possível deletar um cartão que possui faturas pendentes.");
        }
        
        cartao.setAtivo(false);
        cartaoRepository.save(cartao);
        log.info("Cartão desativado (soft delete): cartaoId={}, usuarioId={}", cartaoId, usuarioId);
    }

    /**
     * Consome parte do limite do cartão ao registrar uma DESPESA via cartão.
     * Verifica se o limite disponível é suficiente antes de prosseguir.
     *
     * Este método NÃO persiste nada no cartão. O consumo real do limite acontece
     * quando o TransacaoService chama faturaService.adicionarTransacao(), que incrementa
     * o valorTotal da fatura — e o cálculo dinâmico de limite passa a refletir isso.
     *
     * @param cartaoId  ID do cartão
     * @param valor     valor a consumir do limite (positivo)
     * @param usuarioId ID do dono do cartão
     * @return CartaoEntity validado
     * @throws RecursoNaoEncontradoException se o cartão não existir ou não pertencer ao usuário
     * @throws LimiteInsuficienteException   se o limite disponível for insuficiente
     */
    @Transactional
    public CartaoEntity consumirLimite(Long cartaoId, BigDecimal valor, Long usuarioId) {
        // Bloqueia o cartão para garantir que o cálculo do limite disponível seja atômico
        CartaoEntity cartao = obterCartaoComBloqueioExclusivo(cartaoId, usuarioId);

        BigDecimal limiteDisponivel = calcularLimiteDisponivel(cartao);

        if (limiteDisponivel.compareTo(valor) < 0) {
            log.warn("Limite insuficiente: cartaoId={}, limiteDisponivel={}, valorSolicitado={}", cartaoId, limiteDisponivel, valor);
            throw new LimiteInsuficienteException("Limite insuficiente no cartão. Disponível: R$ " + limiteDisponivel);
        }

        return cartao;
    }

    public CartaoEntity buscarCartaoValidado(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = cartaoRepository.findById(cartaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado"));

        if(!cartao.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Cartão não pertence ao usuário");
        }

        return cartao;
    }

    /**
     * Versão do buscarCartaoValidado com bloqueio pessimista (SELECT FOR UPDATE).
     * Deve ser usado em transações que impactam o limite disponível.
     */
    public CartaoEntity obterCartaoComBloqueioExclusivo(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = cartaoRepository.findByIdWithLock(cartaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado"));

        if(!cartao.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Cartão não pertence ao usuário");
        }

        return cartao;
    }
}
