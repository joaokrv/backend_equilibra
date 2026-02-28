package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.CartaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CartaoResponseDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
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
     *
     * REGRAS que você deve implementar:
     * - Validar que o usuário existe (usar usuarioService.buscarPorIdOuFalhar)
     * - Criar CartaoEntity, setar nome, limite,
     *   diaFechamento, diaVencimento, usuario, ativo=true
     * - Salvar e retornar CartaoResponseDTO
     */
    public CartaoResponseDTO criarCartao(CartaoRegistroRequestDTO dto, Long usuarioId) {
        // TODO: Implementar - validar usuário, criar cartão, salvar, retornar DTO
        return null;
    }

    /**
     * Busca um cartão específico por ID.
     *
     * REGRAS que você deve implementar:
     * - Buscar por ID no repository
     * - Validar que o cartão pertence ao usuário E está ativo
     * - Se não encontrar: lançar RecursoNaoEncontradoException("Cartão não encontrado")
     * - Retornar CartaoResponseDTO
     */
    public CartaoResponseDTO buscarPorId(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = buscarCartaoValidado(cartaoId, usuarioId);
        return new CartaoResponseDTO(cartao);
    }

    /**
     * Calcula o limite disponível de um cartão.
     * Limite Disponível = Limite Total - Soma(Valor Total da Fatura - Valor Pago) de todas as faturas não PAGAS.
     *
     * @param cartaoId ID do cartão
     * @param usuarioId ID do usuário
     * @return Limite disponível em BigDecimal
     *
     * REGRAS que você deve implementar:
     * - Buscar o cartão validado (usar o método privado buscarCartaoValidado).
     * - Buscar todas as faturas do cartão onde o status NÃO é PAGA (usar o faturaRepository.findByCartaoIdAndStatusNot).
     * - Iterar sobre essas faturas e para cada uma fazer: soma das dívidas += (fatura.getValorTotal() - fatura.getValorPago()).
     * - Retornar o (limite do cartão) - (soma das dívidas).
     */
    public BigDecimal calcularLimiteDisponivel(Long cartaoId, Long usuarioId) {
        // TODO: Implementar - buscar cartão, buscar faturas não pagas, calcular e retornar
        return BigDecimal.ZERO;
    }

    /**
     * Lista todos os cartões ativos do usuário.
     *
     * REGRAS que você deve implementar:
     * - Usar cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId)
     * - Converter cada CartaoEntity para CartaoResponseDTO
     * - Retornar a lista (pode ser vazia)
     *
     */
    public List<CartaoResponseDTO> buscarTodosDoUsuario(Long usuarioId) {
        List<CartaoEntity> cartoes =
                cartaoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        return cartoes.stream()
                .map(CartaoResponseDTO::new)
                .toList();
    }

    /**
     * Desativa (soft delete) um cartão.
     *
     * REGRAS que você deve implementar:
     * - Buscar cartão por ID e validar que pertence ao usuário + ativo
     * - Se não encontrar: lançar RecursoNaoEncontradoException("Cartão não encontrado")
     * - Usar faturaRepository.existsByCartaoIdAndStatusNot para verificar se o cartão tem alguma fatura não paga (Status diferente de PAGA).
     * - Se tiver fatura pendente, lançar RegraNegocioException("Não é possível deletar um cartão com faturas em aberto/pendentes").
     * - Setar ativo = false
     * - Salvar
     */
    @Transactional
    public void deletarCartao(Long cartaoId, Long usuarioId) {
        // TODO: Implementar - validação de faturas pendentes e soft delete do cartão
        CartaoEntity cartao = buscarCartaoValidado(cartaoId, usuarioId);

        // Remover esse if e implementar a regra correta descrita acima
        if (cartao.getLimite().compareTo(BigDecimal.ZERO) <= 0) {

        }
    }

    private CartaoEntity buscarCartaoValidado(Long cartaoId, Long usuarioId) {
        CartaoEntity cartao = cartaoRepository.findById(cartaoId)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado"));

        usuarioService.buscarPorIdOuFalhar(usuarioId);

        if(!cartao.getUsuario().getId().equals(usuarioId) || !cartao.isAtivo()) {
            throw new RecursoNaoEncontradoException("Cartão inativo ou não " +
                    "pertence ao usuário");
        }

        return cartao;
    }
}
