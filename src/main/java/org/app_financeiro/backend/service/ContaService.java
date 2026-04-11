package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.ContaResponseDTO;
import org.app_financeiro.backend.mapper.ContaMapper;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.exception.SaldoInsuficienteException;
import org.app_financeiro.backend.repository.ContaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Serviço responsável pelo gerenciamento de Contas Bancárias.
 *
 * Controla o CRUD de contas e as operações de débito/crédito de saldo.
 * Saldo negativo é bloqueado por padrão (Fail-Fast): qualquer tentativa de
 * debitar mais do que o saldo disponível resulta em SaldoInsuficienteException.
 *
 * Este serviço é consumido pelo TransacaoService (ao registrar RECEITAS e DESPESAS)
 * e pelo FaturaService (ao processar o pagamento de uma fatura).
 */
@Service
public class ContaService {

    private static final Logger log = LoggerFactory.getLogger(ContaService.class);

    private final ContaRepository contaRepository;
    private final UsuarioService usuarioService;
    private final ContaMapper contaMapper;

    public ContaService(ContaRepository contaRepository, UsuarioService usuarioService, ContaMapper contaMapper) {
        this.contaRepository = contaRepository;
        this.usuarioService = usuarioService;
        this.contaMapper = contaMapper;
    }

    /**
     * Cria uma nova conta bancária para o usuário.
     * Se o saldo inicial não for informado, assume R$ 0,00.
     *
     * @param dto       Dados da conta a ser criada
     * @param usuarioId ID do dono da conta
     * @return ContaResponseDTO com os dados salvos
     * @throws RecursoNaoEncontradoException se o usuário não existir ou estiver inativo
     */
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

    /**
     * Atualiza o saldo de uma conta (uso genérico para correções manuais).
     *
     * @param contaId ID da conta
     * @param valor Novo saldo absoluto a ser definido
     * @param usuarioId ID do dono da conta
     * @return ContaResponseDTO com o saldo atualizado
     * @throws RecursoNaoEncontradoException se a conta não for encontrada ou não pertencer ao usuário
     */
    @Transactional
    public ContaResponseDTO atualizarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
        ContaEntity conta = obterContaComBloqueioExclusivo(contaId, usuarioId);

        if (valor.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraDeNegocioException("O saldo não pode ser negativo");
        }

        conta.setSaldo(valor);
        contaRepository.save(conta);

        log.info("Saldo atualizado manualmente: contaId={}, novoSaldo={}", contaId, valor);
        return contaMapper.toResponse(conta);
    }

    /**
     * Busca uma conta específica por ID, validando que pertence ao usuário e está ativa.
     *
     * @param contaId   ID da conta
     * @param usuarioId ID do dono da conta
     * @return ContaResponseDTO com os dados da conta
     * @throws RecursoNaoEncontradoException se a conta não for encontrada ou não pertencer ao usuário
     */
    public ContaResponseDTO buscarPorId(Long contaId, Long usuarioId) {
        ContaEntity conta = buscarContaValidada(contaId, usuarioId);
        return contaMapper.toResponse(conta);
    }

    /**
     * Lista todas as contas ativas do usuário.
     *
     * @param usuarioId ID do dono das contas
     * @return Lista de ContaResponseDTO (pode ser vazia)
     */
    public List<ContaResponseDTO> buscarTodasDoUsuario(Long usuarioId) {
        List<ContaEntity> contasDoBanco = contaRepository.findByUsuarioId(usuarioId);

        return contasDoBanco.stream()
                .map(contaMapper::toResponse)
                .toList();
    }

    /**
     * Debita um valor do saldo da conta.
     * Usado por TransacaoService ao criar DESPESA e por InvestimentoService ao depositar.
     * Saldo negativo é bloqueado — lança SaldoInsuficienteException antes de persistir.
     *
     * @param contaId   ID da conta
     * @param valor     valor a debitar (deve ser positivo)
     * @param usuarioId ID do dono da conta
     * @throws RecursoNaoEncontradoException se a conta não for encontrada ou não pertencer ao usuário
     * @throws SaldoInsuficienteException    se o saldo atual for menor que o valor a debitar
     */
    @Transactional
    public ContaEntity debitarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
        ContaEntity conta = obterContaComBloqueioExclusivo(contaId, usuarioId);

        if (conta.getSaldo().compareTo(valor) < 0) {
            log.warn("Saldo insuficiente: contaId={}, saldoAtual={}, valorSolicitado={}", contaId, conta.getSaldo(), valor);
            throw new SaldoInsuficienteException("Saldo insuficiente");
        }

        conta.setSaldo(conta.getSaldo().subtract(valor));
        return contaRepository.save(conta);
    }

    /**
     * Credita um valor ao saldo da conta.
     * Usado por TransacaoService ao criar RECEITA e ao reverter uma DESPESA deletada.
     *
     * @param contaId   ID da conta
     * @param valor     valor a creditar (deve ser positivo)
     * @param usuarioId ID do dono da conta
     * @throws RecursoNaoEncontradoException se a conta não for encontrada ou não pertencer ao usuário
     */
    @Transactional
    public ContaEntity creditarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
        ContaEntity conta = obterContaComBloqueioExclusivo(contaId, usuarioId);

        conta.setSaldo(conta.getSaldo().add(valor));
        return contaRepository.save(conta);
    }

    /**
     * Desativa (soft delete) uma conta.
     * Bloqueia a operação se a conta ainda possuir saldo positivo.
     *
     * @param contaId   ID da conta
     * @param usuarioId ID do dono da conta
     * @throws RecursoNaoEncontradoException se a conta não for encontrada ou não pertencer ao usuário
     * @throws RegraDeNegocioException       se a conta ainda possuir saldo maior que zero
     */
    @Transactional
    public void deletarConta(Long contaId, Long usuarioId) {
        ContaEntity conta = buscarContaValidada(contaId, usuarioId);

        if (conta.getSaldo().compareTo(BigDecimal.ZERO) > 0) {
            log.warn("Tentativa de deletar conta com saldo positivo: contaId={}, saldo={}", contaId, conta.getSaldo());
            throw new RegraDeNegocioException("Não é possível inativar uma conta que ainda possui saldo.");
        }

        conta.setAtivo(false);
        contaRepository.save(conta);
        log.info("Conta desativada (soft delete): contaId={}, usuarioId={}", contaId, usuarioId);
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
