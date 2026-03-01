package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.CartaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CartaoResponseDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.CartaoRepository;
import org.app_financeiro.backend.repository.FaturaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartaoService {

    private final CartaoRepository cartaoRepository;
    private final FaturaRepository faturaRepository;
    private final UsuarioService usuarioService;

    public CartaoService(CartaoRepository cartaoRepository, FaturaRepository faturaRepository, UsuarioService usuarioService) {
        this.cartaoRepository = cartaoRepository;
        this.faturaRepository = faturaRepository;
        this.usuarioService = usuarioService;
    }

    /**
     * Cria um novo cartão de crédito para o usuário.
     */
    public CartaoResponseDTO criarCartao(CartaoRegistroRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        
        CartaoEntity cartao = new CartaoEntity();
        cartao.setNome(dto.getNome());
        cartao.setLimite(dto.getLimite());
        cartao.setDiaFechamento(dto.getDiaFechamento());
        cartao.setDiaVencimento(dto.getDiaVencimento());
        cartao.setUsuario(usuario);
        cartao.setAtivo(true);
        
        CartaoEntity cartaoSalvo = cartaoRepository.save(cartao);
        
        // Limite disponível inicial é 100% do limite total
        return new CartaoResponseDTO(cartaoSalvo, cartaoSalvo.getLimite()); 
    }

    /**
     * Busca um cartão específico por ID e calcula seu limite atual.
     */
    public CartaoResponseDTO buscarPorId(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = buscarCartaoValidado(cartaoId, usuarioId);
        BigDecimal limiteDisponivel = calcularLimiteDisponivel(cartaoId, usuarioId);
        return new CartaoResponseDTO(cartao, limiteDisponivel);
    }

    /**
     * Calcula o limite disponível de um cartão subtraindo as dívidas de faturas não pagas.
     * Limite Disponível = Limite Total - Soma(Valor Total - Valor Pago) de faturas não PAGAS.
     */
    public BigDecimal calcularLimiteDisponivel(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = buscarCartaoValidado(cartaoId, usuarioId);
        
        List<FaturaEntity> faturasPendentes = faturaRepository.findByCartaoIdAndStatusNot(cartaoId, StatusFatura.PAGA);
        
        BigDecimal somaDividas = BigDecimal.ZERO;
        for (FaturaEntity fatura : faturasPendentes) {
            BigDecimal dividaDaFatura = fatura.getValorTotal().subtract(fatura.getValorPago());
            somaDividas = somaDividas.add(dividaDaFatura);
        }
        
        return cartao.getLimite().subtract(somaDividas);
    }

    /**
     * Lista todos os cartões ativos do usuário calculando o limite de cada um em tempo real.
     */
    public List<CartaoResponseDTO> buscarTodosDoUsuario(Long usuarioId) {
        List<CartaoEntity> cartoes = cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        return cartoes.stream()
                .map(cartao -> new CartaoResponseDTO(cartao, calcularLimiteDisponivel(cartao.getId(), usuarioId)))
                .toList();
    }

    /**
     * Desativa (soft delete) um cartão, garantindo que ele não possua faturas pendentes.
     */
    @Transactional
    public void deletarCartao(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = buscarCartaoValidado(cartaoId, usuarioId);
        
        boolean temFaturasPendentes = faturaRepository.existsByCartaoIdAndStatusNot(cartaoId, StatusFatura.PAGA);
        if (temFaturasPendentes) {
            throw new RegraDeNegocioException("Não é possível deletar um cartão que possui faturas pendentes.");
        }
        
        cartao.setAtivo(false);
        cartaoRepository.save(cartao);
    }

    /**
     * Garante que o cartão existe, pertence ao usuário e está ativo.
     */
    private CartaoEntity buscarCartaoValidado(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = cartaoRepository.findById(cartaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado"));

        usuarioService.buscarPorIdOuFalhar(usuarioId);

        if(!cartao.getUsuario().getId().equals(usuarioId) || !cartao.isAtivo()) {
            throw new RecursoNaoEncontradoException("Cartão inativo ou não pertence ao usuário");
        }

        return cartao;
    }
}
