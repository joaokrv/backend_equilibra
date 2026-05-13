package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.InvestimentoAtualizacaoRequestDTO;
import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.TipoInvestimento;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.mapper.InvestimentoMapper;
import org.app_financeiro.backend.entity.ContaEntity;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Gerencia investimentos e metas de poupança com fluxo de depósito/resgate integrado às contas. */
@Service
public class InvestimentoService {

    private static final Logger log = LoggerFactory.getLogger(InvestimentoService.class);

    private final InvestimentoRepository investimentoRepository;
    private final ContaService contaService;
    private final UsuarioService usuarioService;
    private final InvestimentoMapper investimentoMapper;
    private final TransacaoService transacaoService;
    private final PatrimonioHistoricoService patrimonioHistoricoService;

    public InvestimentoService(InvestimentoRepository investimentoRepository,
                               ContaService contaService,
                               UsuarioService usuarioService,
                               InvestimentoMapper investimentoMapper,
                               TransacaoService transacaoService,
                               PatrimonioHistoricoService patrimonioHistoricoService) {
        this.investimentoRepository = investimentoRepository;
        this.contaService = contaService;
        this.usuarioService = usuarioService;
        this.investimentoMapper = investimentoMapper;
        this.transacaoService = transacaoService;
        this.patrimonioHistoricoService = patrimonioHistoricoService;
    }

    @Transactional
    public InvestimentoResponseDTO criarInvestimento(InvestimentoRegistroRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        
        ContaEntity contaOrigem = contaService.buscarContaValidada(dto.contaId(), usuarioId);

        if (dto.meta() != null && dto.valorInicial().compareTo(dto.meta()) > 0) {
            throw new RegraDeNegocioException("O valor inicial não pode ser maior que a meta informada");
        }

        if (dto.valorInicial().compareTo(BigDecimal.ZERO) > 0) {
            registrarMovimentacaoInvestimento(
                    null,
                    dto.descricao(),
                    dto.valorInicial(),
                    dto.contaId(),
                    usuarioId,
                    TipoTransacao.DESPESA,
                    "Aporte inicial em investimento"
            );
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
        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        log.info("Investimento {} '{}' criado para usuário {}. Valor inicial: R$ {}, Meta: {}", investimento.getId(), dto.descricao(), usuarioId, dto.valorInicial(), dto.meta() != null ? "R$ " + dto.meta() : "não definida");
        return investimentoMapper.toResponse(investimento);
    }

    @Transactional
    public InvestimentoResponseDTO adicionarDeposito(Long investimentoId, BigDecimal valor, Long contaId, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);

        registrarMovimentacaoInvestimento(
            investimento,
            investimento.getDescricao(),
            valor,
            contaId,
            usuarioId,
            TipoTransacao.DESPESA,
            "Aporte em investimento"
        );
        
        investimento.setValorAtual(investimento.getValorAtual().add(valor));
        investimento = investimentoRepository.save(investimento);
        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        log.info("Depósito de R$ {} no investimento {}. Novo valor: R$ {}", valor, investimentoId, investimento.getValorAtual());
        
        return investimentoMapper.toResponse(investimento);
    }

    @Transactional
    public InvestimentoResponseDTO resgatarInvestimento(Long investimentoId, BigDecimal valor, Long contaId, Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);
        
        if (investimento.getValorAtual().compareTo(valor) < 0) {
            log.warn("Resgate de R$ {} excede saldo de R$ {} no investimento {}", valor, investimento.getValorAtual(), investimentoId);
            throw new RegraDeNegocioException("Valor de resgate excede o saldo do investimento");
        }
        
        investimento.setValorAtual(investimento.getValorAtual().subtract(valor));
        registrarMovimentacaoInvestimento(
                investimento,
            investimento.getDescricao(),
                valor,
                contaId,
                usuarioId,
                TipoTransacao.RECEITA,
                "Resgate de investimento"
        );
        
        investimento = investimentoRepository.save(investimento);
        patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        log.info("Resgate de R$ {} do investimento {}. Novo valor: R$ {}", valor, investimentoId, investimento.getValorAtual());
        return investimentoMapper.toResponse(investimento);
    }

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

    @Transactional
    public InvestimentoResponseDTO atualizarInvestimento(Long investimentoId,
                                                         InvestimentoAtualizacaoRequestDTO dto,
                                                         Long usuarioId) {
        InvestimentoEntity investimento = buscarInvestimentoValidado(investimentoId, usuarioId);

        if (dto.meta() != null && dto.meta().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("A meta deve ser maior que zero");
        }

        if (dto.meta() != null && investimento.getValorAtual().compareTo(dto.meta()) > 0) {
            throw new RegraDeNegocioException("A meta não pode ser menor que o valor já investido");
        }

        investimento.setDescricao(dto.descricao().trim());
        investimento.setMetaAtual(dto.meta());
        investimento.setTipoInvestimento(dto.tipoInvestimento());
        investimento.setTipoPersonalizado(normalizarTipoPersonalizado(dto.tipoInvestimento(), dto.tipoPersonalizado()));

        investimento = investimentoRepository.save(investimento);
        return investimentoMapper.toResponse(investimento);
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
            throw new RegraDeNegocioException("Resgate o saldo restante (R$ " + investimento.getValorAtual() + ") antes de desativar o investimento");
        }
        
        investimento.setAtivo(false);
        investimentoRepository.save(investimento);
    }


    private InvestimentoEntity buscarInvestimentoValidado(Long investimentoId, Long usuarioId) {
        InvestimentoEntity investimento = investimentoRepository.findById(investimentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Investimento não encontrado"));

        if (!investimento.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Investimento não pertence ao usuário");
        }

        return investimento;
    }

    private String normalizarTipoPersonalizado(TipoInvestimento tipoInvestimento, String tipoPersonalizado) {
        if (tipoInvestimento != TipoInvestimento.OUTRO) {
            return null;
        }

        if (tipoPersonalizado == null || tipoPersonalizado.isBlank()) {
            throw new RegraDeNegocioException("Informe o tipo personalizado quando o tipo for OUTRO");
        }

        return tipoPersonalizado.trim();
    }

    private void registrarMovimentacaoInvestimento(InvestimentoEntity investimento,
                                                   String descricaoInvestimento,
                                                   BigDecimal valor,
                                                   Long contaId,
                                                   Long usuarioId,
                                                   TipoTransacao tipo,
                                                   String prefixoDescricao) {
        String descricao = prefixoDescricao + ": " + descricaoInvestimento;
        String investimentoIdPrefix = investimento != null ? investimento.getId().toString() : "novo";
        String idempotencyKey = "inv-" + investimentoIdPrefix + "-" + UUID.randomUUID();

        TransacaoRegistroRequestDTO registroDTO = new TransacaoRegistroRequestDTO(
                descricao,
                valor,
                LocalDate.now(),
                tipo,
                null,
                MetodoPagamento.TRANSFERENCIA,
                contaId,
                null,
                null,
                null,
                null,
                null,
                idempotencyKey
        );

        transacaoService.criarTransacaoInterna(registroDTO, usuarioId, true);
    }
}
