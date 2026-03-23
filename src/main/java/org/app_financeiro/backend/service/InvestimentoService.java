package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.mapper.InvestimentoMapper;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serviço responsável pelo gerenciamento de investimentos e metas de poupança.
 *
 * Permite criar investimentos, realizar depósitos (debitando de uma conta),
 * resgatar valores (creditando de volta para uma conta), atualizar a meta
 * e desativar investimentos via soft delete.
 *
 * FLUXO DE DINHEIRO:
 * - Depósito:  Conta --(-valor)--> Investimento  (contaService.debitarSaldo + incrementa valorAtual)
 * - Resgate:   Investimento --(-valor)--> Conta  (contaService.creditarSaldo + decrementa valorAtual)
 *
 * O valorInicial é apenas o ponto de partida da meta, NÃO debita de nenhuma conta automaticamente.
 * Para movimentar dinheiro real, use depositar() ou resgatar().
 *
 * REGRAS:
 * 1. O valorInicial não pode ser maior que o valor da meta na criação.
 */
@Service
public class InvestimentoService {

    private static final Logger log = LoggerFactory.getLogger(InvestimentoService.class);

    private final InvestimentoRepository investimentoRepository;
    private final ContaService contaService;
    private final UsuarioService usuarioService;
    private final InvestimentoMapper investimentoMapper;

    public InvestimentoService(InvestimentoRepository investimentoRepository,
                               ContaService contaService,
                               UsuarioService usuarioService,
                               InvestimentoMapper investimentoMapper) {
        this.investimentoRepository = investimentoRepository;
        this.contaService = contaService;
        this.usuarioService = usuarioService;
        this.investimentoMapper = investimentoMapper;
    }

    // =============================================
    // MÉTODOS PÚBLICOS — PARA VOCÊ IMPLEMENTAR
    // =============================================

    /**
     * Cria um novo investimento/meta de poupança para o usuário.
     *
     * REGRAS:
     * 1. Validar que o usuário existe (usuarioService.buscarPorIdOuFalhar)
     * 2. Criar InvestimentoEntity:
     *    - descricao   = dto.getDescricao()
     *    - valorInicial = dto.getValorInicial()
     *    - valorAtual   = dto.getValorInicial() (começa igual ao valorInicial)
     *    - metaAtual    = dto.getMeta()
     *    - usuario      = usuarioEntity
     *    - ativo        = true
     * 3. Salvar e retornar InvestimentoResponseDTO
     *
     * PERGUNTAS PARA REFLETIR:
     * - A criação NÃO debita de nenhuma conta. Por quê?
     *   Porque o valorInicial é o "ponto de partida" da meta, não uma movimentação real.
     * - E se dto.getMeta() for menor que dto.getValorInicial()? Deveria bloquear?
     *   Pense se faz sentido criar um investimento que já nasce "completo".
     *
     * @param dto       Dados do investimento (descrição, valorInicial, meta)
     * @param usuarioId ID do usuário autenticado
     * @return InvestimentoResponseDTO com os dados salvos
     * @throws RecursoNaoEncontradoException se o usuário não existir ou estiver inativo
     */
    @Transactional
    public InvestimentoResponseDTO criarInvestimento(InvestimentoRegistroRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        
        InvestimentoEntity investimento = new InvestimentoEntity();
        investimento.setDescricao(dto.descricao());
        investimento.setValorInicial(dto.valorInicial());
        investimento.setValorAtual(dto.valorInicial());
        investimento.setMetaAtual(dto.meta());
        investimento.setUsuario(usuario);
        investimento.setAtivo(true);
        
        investimento = investimentoRepository.save(investimento);
        log.info("Investimento {} '{}' criado para usuário {}. Valor inicial: R$ {}, Meta: R$ {}", investimento.getId(), dto.descricao(), usuarioId, dto.valorInicial(), dto.meta());
        return investimentoMapper.toResponse(investimento);
    }

    /**
     * Deposita um valor em um investimento existente, debitando de uma conta bancária.
     *
     * REGRAS:
     * 1. Buscar investimento por ID e validar (buscarInvestimentoValidado)
     * 2. Debitar o valor da conta de origem:
     *    contaService.debitarSaldo(contaId, valor, usuarioId)
     *    (já lança SaldoInsuficienteException se saldo insuficiente)
     * 3. Incrementar valorAtual do investimento:
     *    investimento.setValorAtual(investimento.getValorAtual().add(valor))
     * 4. Salvar investimento e retornar InvestimentoResponseDTO
     *
     * PERGUNTAS PARA REFLETIR:
     * - E se o depósito fizer o valorAtual ultrapassar a metaAtual?
     *   O sistema deve permitir ou bloquear? (Sugestão: permitir — o usuário pode querer poupar mais)
     * - Deve validar que o valor é positivo? O DTO já tem @DecimalMin, mas e aqui?
     *
     * @param investimentoId ID do investimento
     * @param valor          Valor a depositar (positivo)
     * @param contaId        ID da conta de onde o dinheiro sai
     * @param usuarioId      ID do usuário autenticado
     * @return InvestimentoResponseDTO com valorAtual atualizado
     * @throws RecursoNaoEncontradoException se o investimento ou conta não existirem
     * @throws SaldoInsuficienteException    se o saldo da conta for insuficiente
     */
    @Transactional
    public InvestimentoResponseDTO adicionarDeposito(Long investimentoId, BigDecimal valor, Long contaId, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);
        
        contaService.debitarSaldo(contaId, valor, usuarioId);
        
        investimento.setValorAtual(investimento.getValorAtual().add(valor));
        investimento = investimentoRepository.save(investimento);
        log.info("Depósito de R$ {} no investimento {}. Novo valor: R$ {}", valor, investimentoId, investimento.getValorAtual());
        
        return investimentoMapper.toResponse(investimento);
    }

    /**
     * Resgata um valor de um investimento, creditando de volta em uma conta bancária.
     *
     * REGRAS:
     * 1. Buscar investimento por ID e validar (buscarInvestimentoValidado)
     * 2. Validar que o valorAtual >= valor a resgatar
     *    Se não: lançar RegraDeNegocioException("Valor de resgate excede o saldo do investimento")
     * 3. Decrementar valorAtual do investimento:
     *    investimento.setValorAtual(investimento.getValorAtual().subtract(valor))
     * 4. Creditar o valor na conta de destino:
     *    contaService.creditarSaldo(contaId, valor, usuarioId)
     * 5. Salvar investimento e retornar InvestimentoResponseDTO
     *
     * PERGUNTAS PARA REFLETIR:
     * - A ordem importa: primeiro subtrai do investimento, depois credita na conta?
     *   E se o creditarSaldo falhar? (Dica: o @Transactional garante rollback total)
     * - E se o resgate zerar o valorAtual? O investimento deve ser desativado automaticamente?
     *   (Sugestão: não — o usuário pode querer depositar novamente depois)
     *
     * @param investimentoId ID do investimento
     * @param valor          Valor a resgatar (positivo)
     * @param contaId        ID da conta que receberá o dinheiro
     * @param usuarioId      ID do usuário autenticado
     * @return InvestimentoResponseDTO com valorAtual atualizado
     * @throws RecursoNaoEncontradoException se o investimento ou conta não existirem
     * @throws RegraDeNegocioException       se o valor de resgate exceder o saldo do investimento
     */
    @Transactional
    public InvestimentoResponseDTO resgatarInvestimento(Long investimentoId, BigDecimal valor, Long contaId, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);
        
        if (investimento.getValorAtual().compareTo(valor) < 0) {
            log.warn("Resgate de R$ {} excede saldo de R$ {} no investimento {}", valor, investimento.getValorAtual(), investimentoId);
            throw new RegraDeNegocioException("Valor de resgate excede o saldo do investimento");
        }
        
        investimento.setValorAtual(investimento.getValorAtual().subtract(valor));
        contaService.creditarSaldo(contaId, valor, usuarioId);
        
        investimento = investimentoRepository.save(investimento);
        log.info("Resgate de R$ {} do investimento {}. Novo valor: R$ {}", valor, investimentoId, investimento.getValorAtual());
        return investimentoMapper.toResponse(investimento);
    }

    /**
     * Atualiza a meta (valor-alvo) de um investimento existente.
     *
     * REGRAS:
     * 1. Buscar investimento por ID e validar (buscarInvestimentoValidado)
     * 2. Validar que a nova meta é positiva (> 0):
     *    Se não: lançar RegraDeNegocioException("A meta deve ser maior que zero")
     * 3. Atualizar investimento.setMetaAtual(novaMeta)
     * 4. Salvar e retornar InvestimentoResponseDTO
     *
     * PERGUNTAS PARA REFLETIR:
     * - E se a nova meta for menor que o valorAtual? O investimento já está "completo".
     *   Deveria bloquear, alertar, ou simplesmente permitir?
     *
     * @param investimentoId ID do investimento
     * @param novaMeta       Novo valor da meta (positivo)
     * @param usuarioId      ID do usuário autenticado
     * @return InvestimentoResponseDTO com metaAtual atualizada
     * @throws RecursoNaoEncontradoException se o investimento não existir
     * @throws RegraDeNegocioException       se a nova meta não for positiva
     */
    @Transactional
    public InvestimentoResponseDTO atualizarMeta(Long investimentoId, BigDecimal novaMeta, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);
        
        if (novaMeta.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Tentativa de definir meta <= 0 no investimento {}", investimentoId);
            throw new RegraDeNegocioException("A meta deve ser maior que zero");
        }
        
        investimento.setMetaAtual(novaMeta);
        investimento = investimentoRepository.save(investimento);
        
        return investimentoMapper.toResponse(investimento);
    }

    /**
     * Lista todos os investimentos ativos do usuário.
     *
     * REGRAS:
     * 1. Usar investimentoRepository.findByUsuarioId(usuarioId)
     * 2. Converter cada InvestimentoEntity para InvestimentoResponseDTO
     *    com .stream().map(InvestimentoResponseDTO::new).toList()
     * 3. Retornar a lista (pode ser vazia)
     *
     * @param usuarioId ID do usuário autenticado
     * @return Lista de InvestimentoResponseDTO (pode ser vazia)
     */
    public List<InvestimentoResponseDTO> buscarTodosDoUsuario(Long usuarioId) {
        return investimentoRepository.findByUsuarioId(usuarioId)
                .stream()
                .map(investimentoMapper::toResponse)
                .toList();
    }

    /**
     * Desativa (soft delete) um investimento.
     *
     * REGRAS:
     * 1. Buscar investimento por ID e validar (buscarInvestimentoValidado)
     * 2. Validar que o valorAtual == 0 (não pode desativar com dinheiro dentro):
     *    Se valorAtual > 0: lançar RegraDeNegocioException(
     *      "Resgate o saldo restante (R$ X) antes de desativar o investimento")
     * 3. Setar investimento.setAtivo(false)
     * 4. Salvar
     *
     * PERGUNTAS PARA REFLETIR:
     * - Por que não permitir desativar com saldo? Porque o dinheiro "desapareceria"
     *   da visão do usuário — ficaria preso em um investimento invisível.
     * - Alternativa: resgatar automaticamente para uma conta antes de desativar.
     *   Mas isso exigiria que o usuário informe a conta de destino no request.
     *
     * @param investimentoId ID do investimento a desativar
     * @param usuarioId      ID do usuário autenticado
     * @throws RecursoNaoEncontradoException se o investimento não existir
     * @throws RegraDeNegocioException       se o investimento ainda possuir saldo
     */
    @Transactional
    public void deletarInvestimento(Long investimentoId, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);
        
        if (investimento.getValorAtual().compareTo(BigDecimal.ZERO) > 0) {
            throw new RegraDeNegocioException("Resgate o saldo restante (R$ " + investimento.getValorAtual() + ") antes de desativar o investimento");
        }
        
        investimento.setAtivo(false);
        investimentoRepository.save(investimento);
    }

    // =============================================
    // MÉTODO PRIVADO DE VALIDAÇÃO
    // =============================================

    /**
     * Busca um investimento por ID e valida que pertence ao usuário e está ativo.
     * Padrão idêntico ao buscarContaValidada/buscarCartaoValidado dos outros services.
     *
     * @param investimentoId ID do investimento
     * @param usuarioId      ID do usuário autenticado
     * @return InvestimentoEntity validado
     * @throws RecursoNaoEncontradoException se não existir, não pertencer ao usuário ou estiver inativo
     */
    private InvestimentoEntity buscarInvestimentoValidado(Long investimentoId, Long usuarioId) {
        InvestimentoEntity investimento = investimentoRepository.findById(investimentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Investimento não encontrado"));

        if (!investimento.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Investimento não pertence ao usuário");
        }

        return investimento;
    }
}
