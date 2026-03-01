package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class InvestimentoService {

    private final InvestimentoRepository investimentoRepository;
    private final ContaService contaService;
    private final UsuarioService usuarioService;

    public InvestimentoService(InvestimentoRepository investimentoRepository,
                               ContaService contaService,
                               UsuarioService usuarioService) {
        this.investimentoRepository = investimentoRepository;
        this.contaService = contaService;
        this.usuarioService = usuarioService;
    }

    /**
     * Cria um novo investimento/meta de poupança para o usuário.
     *
     * REGRAS:
     * - Validar que o usuário existe (usuarioService.buscarPorIdOuFalhar)
     * - Criar InvestimentoEntity:
     *   - descricao  = dto.getDescricao()
     *   - valorInicial = dto.getValorInicial()
     *   - valorAtual   = dto.getValorInicial() (começa igual ao valorInicial)
     *   - metaAtual    = dto.getMeta()
     *   - usuario      = usuarioEntity
     *   - ativo        = true
     * - Salvar e retornar InvestimentoResponseDTO
     *
     * A criação NÃO debita de nenhuma conta automaticamente.
     * O valorInicial é apenas o ponto de partida da meta.
     * Para debitar de uma conta, use depositar() após criar.
     */
    public InvestimentoResponseDTO criarInvestimento(InvestimentoRegistroRequestDTO dto, Long usuarioId) {
        // TODO: Implementar - validar usuário, criar investimento, salvar, retornar DTO
        return null;
    }

    /**
     * Deposita um valor em um investimento existente, debitando de uma conta.
     *
     * REGRAS:
     * 1. Buscar investimento por ID e validar que pertence ao usuário + ativo
     *    (lançar RecursoNaoEncontradoException se não encontrar)
     * 2. Debitar o valor da conta de origem:
     *    contaService.debitarSaldo(contaId, valor, usuarioId)
     *    (já bloqueia automaticamente se saldo insuficiente)
     * 3. Incrementar valorAtual: investimento.setValorAtual(investimento.getValorAtual().add(valor))
     * 4. Salvar investimento e retornar InvestimentoResponseDTO
     *
     * @param investimentoId ID do investimento
     * @param valor          valor a depositar (positivo)
     * @param contaId        ID da conta de onde o dinheiro sai
     * @param usuarioId      ID do dono
     */
    @Transactional
    public InvestimentoResponseDTO adicionarDeposito(Long investimentoId, BigDecimal valor, Long contaId, Long usuarioId) {
        // TODO: Implementar - buscar investimento, debitar conta, incrementar valorAtual, salvar
        return null;
    }

    /**
     * Lista todos os investimentos ativos do usuário.
     *
     * REGRAS:
     * - Usar investimentoRepository.findByUsuarioIdAndAtivoTrue(usuarioId)
     * - Converter cada InvestimentoEntity para InvestimentoResponseDTO com .stream().map(InvestimentoResponseDTO::new).toList()
     * - Retornar a lista (pode ser vazia)
     */
    public List<InvestimentoResponseDTO> buscarTodosDoUsuario(Long usuarioId) {
        // TODO: Implementar - buscar por usuário, converter para DTOs
        return null;
    }
}
