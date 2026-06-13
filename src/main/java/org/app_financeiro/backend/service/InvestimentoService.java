package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.InvestimentoAtualizacaoRequestDTO;
import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.MovimentacaoAtualizacaoRequestDTO;
import org.app_financeiro.backend.dto.request.RendimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.dto.response.MovimentacaoInvestimentoResponseDTO;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.entity.MovimentacaoInvestimentoEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.TipoInvestimento;
import org.app_financeiro.backend.enums.TipoMovimentacaoInvestimento;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.mapper.InvestimentoMapper;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.MovimentacaoInvestimentoRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class InvestimentoService {

    private static final Logger log = LoggerFactory.getLogger(InvestimentoService.class);

    private final InvestimentoRepository investimentoRepository;
    private final MovimentacaoInvestimentoRepository movimentacaoRepository;
    private final TransacaoRepository transacaoRepository;
    private final ContaService contaService;
    private final UsuarioService usuarioService;
    private final InvestimentoMapper investimentoMapper;
    private final TransacaoService transacaoService;
    private final PatrimonioHistoricoService patrimonioHistoricoService;
    private final MovimentacaoFinanceiraService movimentacaoFinanceiraService;

    public InvestimentoService(InvestimentoRepository investimentoRepository,
                               MovimentacaoInvestimentoRepository movimentacaoRepository,
                               TransacaoRepository transacaoRepository,
                               ContaService contaService,
                               UsuarioService usuarioService,
                               InvestimentoMapper investimentoMapper,
                               TransacaoService transacaoService,
                               PatrimonioHistoricoService patrimonioHistoricoService,
                               MovimentacaoFinanceiraService movimentacaoFinanceiraService) {
        this.investimentoRepository = investimentoRepository;
        this.movimentacaoRepository = movimentacaoRepository;
        this.transacaoRepository = transacaoRepository;
        this.contaService = contaService;
        this.usuarioService = usuarioService;
        this.investimentoMapper = investimentoMapper;
        this.transacaoService = transacaoService;
        this.patrimonioHistoricoService = patrimonioHistoricoService;
        this.movimentacaoFinanceiraService = movimentacaoFinanceiraService;
    }

    @Transactional
    public InvestimentoResponseDTO criarInvestimento(InvestimentoRegistroRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        ContaEntity contaOrigem = contaService.buscarContaValidada(dto.contaId(), usuarioId);

        if (dto.meta() != null && dto.valorInicial().compareTo(dto.meta()) > 0) {
            throw new RegraDeNegocioException("error.investimento.valor_inicial_maior_meta",
                    "O valor inicial não pode ser maior que a meta informada");
        }

        InvestimentoEntity investimento = new InvestimentoEntity();
        investimento.setDescricao(dto.descricao());
        investimento.setValorInicial(dto.valorInicial());
        investimento.setValorAtual(dto.valorInicial());
        investimento.setMetaAtual(dto.meta());
        investimento.setTipoInvestimento(dto.tipoInvestimento());
        investimento.setTipoPersonalizado(normalizarTipoPersonalizado(dto.tipoInvestimento(), dto.tipoPersonalizado()));
        investimento.setContaOrigem(contaOrigem);
        investimento.setUsuario(usuario);
        investimento.setAtivo(true);

        if (dto.contaDestinoId() != null) {
            ContaEntity contaDestino = contaService.buscarContaValidada(dto.contaDestinoId(), usuarioId);
            investimento.setContaDestino(contaDestino);
        }

        investimento = investimentoRepository.save(investimento);

        if (dto.valorInicial().compareTo(BigDecimal.ZERO) > 0) {
            Long transacaoId = criarTransacaoInvestimento(
                    investimento.getId(), dto.descricao(), dto.valorInicial(),
                    dto.contaId(), usuarioId, TipoTransacao.DESPESA, "Aporte inicial em investimento");
            gravarMovimentacao(investimento.getId(), usuarioId, TipoMovimentacaoInvestimento.APORTE,
                    dto.valorInicial(), LocalDate.now(), dto.contaId(), transacaoId, null);
        }

        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        log.info("Investimento {} '{}' criado para usuário {}", investimento.getId(), dto.descricao(), usuarioId);
        return investimentoMapper.toResponse(investimento);
    }

    @Transactional
    public InvestimentoResponseDTO adicionarDeposito(Long investimentoId, BigDecimal valor, Long contaId, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);

        Long transacaoId = criarTransacaoInvestimento(
                investimentoId, investimento.getDescricao(), valor,
                contaId, usuarioId, TipoTransacao.DESPESA, "Aporte em investimento");

        investimento.setValorAtual(investimento.getValorAtual().add(valor));
        investimento = investimentoRepository.save(investimento);

        gravarMovimentacao(investimentoId, usuarioId, TipoMovimentacaoInvestimento.APORTE,
                valor, LocalDate.now(), contaId, transacaoId, null);

        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        log.info("Aporte de R$ {} no investimento {}. Novo valor: R$ {}", valor, investimentoId, investimento.getValorAtual());
        return investimentoMapper.toResponse(investimento);
    }

    @Transactional
    public InvestimentoResponseDTO resgatarInvestimento(Long investimentoId, BigDecimal valor, Long contaId, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);

        if (investimento.getValorAtual().compareTo(valor) < 0) {
            throw new RegraDeNegocioException("error.investimento.resgate_excede",
                    "Valor de resgate excede o saldo do investimento");
        }

        Long transacaoId = criarTransacaoInvestimento(
                investimentoId, investimento.getDescricao(), valor,
                contaId, usuarioId, TipoTransacao.RECEITA, "Resgate de investimento");

        investimento.setValorAtual(investimento.getValorAtual().subtract(valor));
        investimento = investimentoRepository.save(investimento);

        gravarMovimentacao(investimentoId, usuarioId, TipoMovimentacaoInvestimento.RESGATE,
                valor, LocalDate.now(), contaId, transacaoId, null);

        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        log.info("Resgate de R$ {} do investimento {}. Novo valor: R$ {}", valor, investimentoId, investimento.getValorAtual());
        return investimentoMapper.toResponse(investimento);
    }

    @Transactional
    public InvestimentoResponseDTO atualizarMeta(Long investimentoId, BigDecimal novaMeta, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);
        if (novaMeta.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("error.investimento.meta_maior_zero", "A meta deve ser maior que zero");
        }
        investimento.setMetaAtual(novaMeta);
        return investimentoMapper.toResponse(investimentoRepository.save(investimento));
    }

    @Transactional
    public InvestimentoResponseDTO atualizarInvestimento(Long investimentoId,
                                                         InvestimentoAtualizacaoRequestDTO dto,
                                                         Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);

        if (dto.meta() != null && dto.meta().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("error.investimento.meta_maior_zero", "A meta deve ser maior que zero");
        }
        if (dto.meta() != null && investimento.getValorAtual().compareTo(dto.meta()) > 0) {
            throw new RegraDeNegocioException("error.investimento.meta_menor_investido",
                    "A meta não pode ser menor que o valor já investido");
        }

        investimento.setDescricao(dto.descricao().trim());
        investimento.setMetaAtual(dto.meta());
        investimento.setTipoInvestimento(dto.tipoInvestimento());
        investimento.setTipoPersonalizado(normalizarTipoPersonalizado(dto.tipoInvestimento(), dto.tipoPersonalizado()));
        return investimentoMapper.toResponse(investimentoRepository.save(investimento));
    }

    @Transactional(readOnly = true)
    public List<InvestimentoResponseDTO> buscarTodosDoUsuario(Long usuarioId) {
        return investimentoRepository.findByUsuarioId(usuarioId)
                .stream()
                .map(investimentoMapper::toResponse)
                .toList();
    }

    @Transactional
    public void deletarInvestimento(Long investimentoId, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);
        if (investimento.getValorAtual().compareTo(BigDecimal.ZERO) > 0) {
            throw new RegraDeNegocioException("error.investimento.resgatar_antes_desativar",
                    "Resgate o saldo restante (R$ " + investimento.getValorAtual() + ") antes de desativar o investimento",
                    investimento.getValorAtual());
        }
        investimento.setAtivo(false);
        investimentoRepository.save(investimento);
    }

    @Transactional
    public MovimentacaoInvestimentoResponseDTO registrarRendimento(RendimentoRegistroRequestDTO dto, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(dto.investimentoId(), usuarioId);

        investimento.setValorAtual(investimento.getValorAtual().add(dto.valor()));
        investimentoRepository.save(investimento);

        MovimentacaoInvestimentoEntity mov = gravarMovimentacao(
                dto.investimentoId(), usuarioId, TipoMovimentacaoInvestimento.RENDIMENTO,
                dto.valor(), dto.data(), null, null, dto.observacao());

        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        log.info("Rendimento de R$ {} registrado no investimento {}", dto.valor(), dto.investimentoId());

        return toResponseDTO(mov, investimento.getDescricao(), null);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoInvestimentoResponseDTO> listarMovimentacoes(
            Long usuarioId,
            TipoMovimentacaoInvestimento tipo,
            Long investimentoId,
            LocalDate dataInicio,
            LocalDate dataFim,
            Pageable pageable) {

        LocalDate inicio = dataInicio != null ? dataInicio : LocalDate.now().minusDays(30);
        LocalDate fim = dataFim != null ? dataFim : LocalDate.now();

        if (inicio.isAfter(fim)) {
            throw new RegraDeNegocioException("error.intervalo.data_inicio_posterior",
                    "A data de início não pode ser posterior à data de fim");
        }

        return movimentacaoRepository.buscarExtrato(usuarioId, tipo, investimentoId, inicio, fim, pageable);
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoInvestimentoResponseDTO> buscarPreview(Long usuarioId) {
        return movimentacaoRepository.buscarPreview(usuarioId, PageRequest.of(0, 5));
    }

    @Transactional
    public MovimentacaoInvestimentoResponseDTO editarMovimentacao(Long movId,
                                                                   MovimentacaoAtualizacaoRequestDTO dto,
                                                                   Long usuarioId) {
        MovimentacaoInvestimentoEntity mov = movimentacaoRepository.findByIdAndUsuarioId(movId, usuarioId)
                .filter(MovimentacaoInvestimentoEntity::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentação não encontrada"));

        InvestimentoEntity investimento = buscarInvestimentoValidado(mov.getInvestimentoId(), usuarioId);

        return switch (mov.getTipo()) {
            case RENDIMENTO -> editarRendimento(mov, dto, investimento, usuarioId);
            case APORTE, RESGATE -> editarAporteOuResgate(mov, dto, investimento, usuarioId);
        };
    }

    private MovimentacaoInvestimentoResponseDTO editarRendimento(MovimentacaoInvestimentoEntity mov,
                                                                  MovimentacaoAtualizacaoRequestDTO dto,
                                                                  InvestimentoEntity investimento,
                                                                  Long usuarioId) {
        BigDecimal delta = dto.valor().subtract(mov.getValor());
        investimento.setValorAtual(investimento.getValorAtual().add(delta));
        investimentoRepository.save(investimento);

        mov.setValor(dto.valor());
        mov.setData(dto.data());
        mov.setObservacao(dto.observacao());
        movimentacaoRepository.save(mov);

        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        return toResponseDTO(mov, investimento.getDescricao(), null);
    }

    private MovimentacaoInvestimentoResponseDTO editarAporteOuResgate(MovimentacaoInvestimentoEntity mov,
                                                                       MovimentacaoAtualizacaoRequestDTO dto,
                                                                       InvestimentoEntity investimento,
                                                                       Long usuarioId) {
        if (dto.contaId() == null) {
            throw new RegraDeNegocioException("error.investimento.conta_obrigatoria",
                    "Conta bancária é obrigatória para editar aportes e resgates");
        }

        excluirMovimentacaoInterno(mov, investimento, usuarioId);

        MovimentacaoInvestimentoEntity nova;
        if (mov.getTipo() == TipoMovimentacaoInvestimento.APORTE) {
            nova = adicionarDepositoRetornandoMovimentacao(investimento, dto.valor(), dto.contaId(), dto.data(), dto.observacao(), usuarioId);
        } else {
            nova = resgatarRetornandoMovimentacao(investimento, dto.valor(), dto.contaId(), dto.data(), dto.observacao(), usuarioId);
        }

        String nomeConta = contaService.buscarContaValidada(dto.contaId(), usuarioId).getNome();
        return toResponseDTO(nova, investimento.getDescricao(), nomeConta);
    }

    @Transactional
    public void excluirMovimentacao(Long movId, Long usuarioId) {
        MovimentacaoInvestimentoEntity mov = movimentacaoRepository.findByIdAndUsuarioId(movId, usuarioId)
                .filter(MovimentacaoInvestimentoEntity::isAtivo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimentação não encontrada"));

        InvestimentoEntity investimento = buscarInvestimentoValidado(mov.getInvestimentoId(), usuarioId);
        excluirMovimentacaoInterno(mov, investimento, usuarioId);
    }

    private void excluirMovimentacaoInterno(MovimentacaoInvestimentoEntity mov,
                                            InvestimentoEntity investimento,
                                            Long usuarioId) {
        switch (mov.getTipo()) {
            case RENDIMENTO -> {
                investimento.setValorAtual(investimento.getValorAtual().subtract(mov.getValor()));
            }
            case APORTE -> {
                TransacaoEntity transacao = transacaoRepository.findById(mov.getTransacaoId())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Transação do aporte não encontrada"));
                movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(transacao, usuarioId);
                transacao.setAtivo(false);
                investimento.setValorAtual(investimento.getValorAtual().subtract(mov.getValor()));
            }
            case RESGATE -> {
                TransacaoEntity transacao = transacaoRepository.findById(mov.getTransacaoId())
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Transação do resgate não encontrada"));
                movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(transacao, usuarioId);
                transacao.setAtivo(false);
                investimento.setValorAtual(investimento.getValorAtual().add(mov.getValor()));
            }
        }

        investimentoRepository.save(investimento);
        mov.setAtivo(false);
        movimentacaoRepository.save(mov);
        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        log.info("Movimentação {} ({}) excluída do investimento {}", mov.getId(), mov.getTipo(), mov.getInvestimentoId());
    }

    private MovimentacaoInvestimentoEntity adicionarDepositoRetornandoMovimentacao(
            InvestimentoEntity investimento, BigDecimal valor, Long contaId,
            LocalDate data, String observacao, Long usuarioId) {

        Long transacaoId = criarTransacaoInvestimento(
                investimento.getId(), investimento.getDescricao(), valor,
                contaId, usuarioId, TipoTransacao.DESPESA, "Aporte em investimento");

        investimento.setValorAtual(investimento.getValorAtual().add(valor));
        investimentoRepository.save(investimento);

        MovimentacaoInvestimentoEntity mov = gravarMovimentacao(
                investimento.getId(), usuarioId, TipoMovimentacaoInvestimento.APORTE,
                valor, data, contaId, transacaoId, observacao);

        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        return mov;
    }

    private MovimentacaoInvestimentoEntity resgatarRetornandoMovimentacao(
            InvestimentoEntity investimento, BigDecimal valor, Long contaId,
            LocalDate data, String observacao, Long usuarioId) {

        if (investimento.getValorAtual().compareTo(valor) < 0) {
            throw new RegraDeNegocioException("error.investimento.resgate_excede",
                    "Valor de resgate excede o saldo do investimento");
        }

        Long transacaoId = criarTransacaoInvestimento(
                investimento.getId(), investimento.getDescricao(), valor,
                contaId, usuarioId, TipoTransacao.RECEITA, "Resgate de investimento");

        investimento.setValorAtual(investimento.getValorAtual().subtract(valor));
        investimentoRepository.save(investimento);

        MovimentacaoInvestimentoEntity mov = gravarMovimentacao(
                investimento.getId(), usuarioId, TipoMovimentacaoInvestimento.RESGATE,
                valor, data, contaId, transacaoId, observacao);

        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        return mov;
    }

    /** Cria transação interna de investimento e retorna o id da transação criada. */
    private Long criarTransacaoInvestimento(Long investimentoId, String descricaoInvestimento,
                                             BigDecimal valor, Long contaId, Long usuarioId,
                                             TipoTransacao tipo, String prefixo) {
        String idempotencyKey = "inv-" + investimentoId + "-" + UUID.randomUUID();
        TransacaoRegistroRequestDTO dto = new TransacaoRegistroRequestDTO(
                prefixo + ": " + descricaoInvestimento,
                valor, LocalDate.now(), tipo, null,
                MetodoPagamento.TRANSFERENCIA, contaId,
                null, null, null, null, null, idempotencyKey);

        return transacaoService.criarTransacaoInterna(dto, usuarioId, true).id();
    }

    private MovimentacaoInvestimentoEntity gravarMovimentacao(Long investimentoId, Long usuarioId,
                                                               TipoMovimentacaoInvestimento tipo,
                                                               BigDecimal valor, LocalDate data,
                                                               Long contaId, Long transacaoId,
                                                               String observacao) {
        MovimentacaoInvestimentoEntity mov = new MovimentacaoInvestimentoEntity();
        mov.setInvestimentoId(investimentoId);
        mov.setUsuarioId(usuarioId);
        mov.setTipo(tipo);
        mov.setValor(valor);
        mov.setData(data);
        mov.setContaId(contaId);
        mov.setTransacaoId(transacaoId);
        mov.setObservacao(observacao);
        return movimentacaoRepository.save(mov);
    }

    private MovimentacaoInvestimentoResponseDTO toResponseDTO(MovimentacaoInvestimentoEntity mov,
                                                               String descricaoInvestimento,
                                                               String nomeConta) {
        return new MovimentacaoInvestimentoResponseDTO(
                mov.getId(), mov.getTipo(), mov.getValor(), mov.getData(),
                descricaoInvestimento, mov.getInvestimentoId(), nomeConta, mov.getObservacao());
    }

    private InvestimentoEntity buscarInvestimentoValidado(Long investimentoId, Long usuarioId) {
        InvestimentoEntity investimento = investimentoRepository.findById(investimentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Investimento não encontrado"));
        if (!investimento.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Investimento não pertence ao usuário");
        }
        return investimento;
    }

    private String normalizarTipoPersonalizado(TipoInvestimento tipo, String tipoPersonalizado) {
        if (tipo != TipoInvestimento.OUTRO) return null;
        if (tipoPersonalizado == null || tipoPersonalizado.isBlank()) {
            throw new RegraDeNegocioException("error.investimento.tipo_personalizado",
                    "Informe o tipo personalizado quando o tipo for OUTRO");
        }
        return tipoPersonalizado.trim();
    }
}
