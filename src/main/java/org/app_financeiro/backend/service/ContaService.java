package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.ContaResponseDTO;
import org.app_financeiro.backend.mapper.ContaMapper;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.exception.SaldoInsuficienteException;
import org.app_financeiro.backend.repository.CartaoRepository;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.TransacaoRecorrenteRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Gerencia contas bancárias: CRUD e operações de débito/crédito de saldo. */
@Service
public class ContaService {

    private static final Logger log = LoggerFactory.getLogger(ContaService.class);

    private final ContaRepository contaRepository;
    private final InvestimentoRepository investimentoRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoRecorrenteRepository transacaoRecorrenteRepository;
    private final CartaoRepository cartaoRepository;
    private final UsuarioService usuarioService;
    private final ContaMapper contaMapper;

    public ContaService(ContaRepository contaRepository,
                        InvestimentoRepository investimentoRepository,
                        TransacaoRepository transacaoRepository,
                        TransacaoRecorrenteRepository transacaoRecorrenteRepository,
                        CartaoRepository cartaoRepository,
                        UsuarioService usuarioService,
                        ContaMapper contaMapper) {
        this.contaRepository = contaRepository;
        this.investimentoRepository = investimentoRepository;
        this.transacaoRepository = transacaoRepository;
        this.transacaoRecorrenteRepository = transacaoRecorrenteRepository;
        this.cartaoRepository = cartaoRepository;
        this.usuarioService = usuarioService;
        this.contaMapper = contaMapper;
    }

    @Transactional
    public ContaResponseDTO criarConta(ContaRegistroRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);

        BigDecimal saldoInicial = dto.saldo() != null ? dto.saldo() : BigDecimal.ZERO;

        ContaEntity conta = new ContaEntity();
        conta.setSaldo(saldoInicial);
        conta.setNome(dto.nome());
        conta.setUsuario(usuario);
        conta.setAtivo(true);
        contaRepository.save(conta);

        log.info("Conta criada: id={}, nome='{}', usuarioId={}", conta.getId(), conta.getNome(), usuarioId);
        return contaMapper.toResponse(conta);
    }

    @Transactional
    public ContaResponseDTO atualizarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
        ContaEntity conta = obterContaComBloqueioExclusivo(contaId, usuarioId);

        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraDeNegocioException("error.conta.saldo_negativo", "O saldo não pode ser negativo");
        }

        conta.setSaldo(valor);
        contaRepository.save(conta);

        log.info("Saldo atualizado manualmente: contaId={}, novoSaldo={}", contaId, valor);
        return contaMapper.toResponse(conta);
    }

    public ContaResponseDTO buscarPorId(Long contaId, Long usuarioId) {
        ContaEntity conta = buscarContaValidada(contaId, usuarioId);
        return contaMapper.toResponse(conta);
    }

    public List<ContaResponseDTO> buscarTodasDoUsuario(Long usuarioId) {
        List<ContaEntity> contasDoBanco = contaRepository.findByUsuarioId(usuarioId);

        return contasDoBanco.stream()
                .map(contaMapper::toResponse)
                .toList();
    }

    /** Saldo negativo é bloqueado — lança SaldoInsuficienteException antes de persistir. */
    @Transactional
    public ContaEntity debitarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
        ContaEntity conta = obterContaComBloqueioExclusivo(contaId, usuarioId);

        if (conta.getSaldo().compareTo(valor) < 0) {
            log.warn("Saldo insuficiente: contaId={}, saldoAtual={}, valorSolicitado={}", contaId, conta.getSaldo(), valor);
            throw new SaldoInsuficienteException(conta.getSaldo());
        }

        conta.setSaldo(conta.getSaldo().subtract(valor));
        return contaRepository.save(conta);
    }

    @Transactional
    public ContaEntity creditarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
        ContaEntity conta = obterContaComBloqueioExclusivo(contaId, usuarioId);

        conta.setSaldo(conta.getSaldo().add(valor));
        return contaRepository.save(conta);
    }

    @Transactional
    public void deletarConta(Long contaId, Long usuarioId) {
        ContaEntity conta = buscarContaValidada(contaId, usuarioId);

        if (conta.getSaldo().compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Tentativa de deletar conta com saldo positivo: contaId={}, saldo={}", contaId, conta.getSaldo());
            throw new RegraDeNegocioException("error.conta.inativar_com_saldo", "Não é possível inativar uma conta que ainda possui saldo.");
        }

        int investimentosInativados = investimentoRepository.inativarVinculadosAConta(usuarioId, contaId);
        int transacoesInativadas = transacaoRepository.inativarPorConta(usuarioId, contaId);
        int recorrenciasInativadas = transacaoRecorrenteRepository.inativarPorConta(usuarioId, contaId);
        int cartoesDesvinculados = cartaoRepository.desvincularConta(usuarioId, contaId);

        conta.setAtivo(false);
        contaRepository.save(conta);
        log.info("Conta desativada (soft delete em cascata): contaId={}, usuarioId={}, investimentosInativados={}, transacoesInativadas={}, recorrenciasInativadas={}, cartoesDesvinculados={}",
                contaId, usuarioId, investimentosInativados, transacoesInativadas, recorrenciasInativadas, cartoesDesvinculados);
    }

    public ContaEntity buscarContaValidada(Long contaId, Long usuarioId) {
        ContaEntity conta = contaRepository.findById(contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));

        if (!conta.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Conta não pertence ao usuário");
        }

        return conta;
    }

    /**
     * Versão do buscarContaValidada que aplica Pessimistic Lock (SELECT FOR UPDATE).
     * Deve ser usado apenas em métodos @Transactional que realizam débitos ou créditos.
     */
    public ContaEntity obterContaComBloqueioExclusivo(Long contaId, Long usuarioId) {
        ContaEntity conta = contaRepository.findByIdWithLock(contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));

        if (!conta.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Conta não pertence ao usuário");
        }

        return conta;
    }
}
