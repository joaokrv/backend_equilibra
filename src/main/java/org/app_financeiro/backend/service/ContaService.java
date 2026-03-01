package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.ContaResponseDTO;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.exception.SaldoInsuficienteException;
import org.app_financeiro.backend.repository.ContaRepository;
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

    private final ContaRepository contaRepository;
    private final UsuarioService usuarioService;

    public ContaService(ContaRepository contaRepository, UsuarioService usuarioService) {
        this.contaRepository = contaRepository;
        this.usuarioService = usuarioService;
    }

    /**
     * Cria uma nova conta bancária para o usuário.
     * Se o saldo inicial não for informado, assume R$ 0,00.
     *
     * @param dto Dados da conta a ser criada
     * @param usuarioId ID do dono da conta
     * @return ContaResponseDTO com os dados salvos
     */
    public ContaResponseDTO criarConta(ContaRegistroRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);

        if (dto.getSaldo() == null) {
            dto.setSaldo(BigDecimal.ZERO);
        }

        ContaEntity conta = new ContaEntity();
        conta.setSaldo(dto.getSaldo());
        conta.setNome(dto.getNome());
        conta.setUsuario(usuario);
        conta.setAtivo(true);
        contaRepository.save(conta);

        return new ContaResponseDTO(conta);
    }

    /**
     * Atualiza o saldo de uma conta (uso genérico para correções manuais).
     *
     * @param contaId ID da conta
     * @param valor Novo saldo absoluto a ser definido
     * @param usuarioId ID do dono da conta
     * @return ContaResponseDTO com o saldo atualizado
     */
    public ContaResponseDTO atualizarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
        ContaEntity conta = buscarContaValidada(contaId, usuarioId);

        conta.setSaldo(valor);
        contaRepository.save(conta);

        return new ContaResponseDTO(conta);
    }

    /**
     * Busca uma conta específica por ID.
     *
     * REGRAS:
     * - Buscar por ID no repository
     * - Validar que a conta pertence ao usuário E está ativa
     * - Se não encontrar: lançar RecursoNaoEncontradoException("Conta não encontrada")
     * - Retornar ContaResponseDTO
     */
    public ContaResponseDTO buscarPorId(Long contaId, Long usuarioId) {
        ContaEntity conta = buscarContaValidada(contaId, usuarioId);
        return new ContaResponseDTO(conta);
    }

    /**
     * Lista todas as contas ativas do usuário.
     *
     * REGRAS:
     * - Usar contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId)
     * - Converter cada ContaEntity para ContaResponseDTO
     * - Retornar a lista (pode ser vazia)
     *
     */
    public List<ContaResponseDTO> buscarTodasDoUsuario(Long usuarioId) {
        List<ContaEntity> contasDoBanco = contaRepository.findByUsuarioIdAndAtivoTrue(usuarioId);

        return contasDoBanco.stream()
                .map(ContaResponseDTO::new)
                .toList();
    }

    /**
     * Debita um valor do saldo da conta.
     * Usado por TransacaoService ao criar DESPESA e por InvestimentoService ao depositar.
     *
     * REGRAS:
     * - Buscar conta por ID e validar que pertence ao usuário + ativa
     * - Se não encontrar: lançar RecursoNaoEncontradoException("Conta não encontrada")
     * - Calcular novoSaldo = saldoAtual - valor
     * - Se novoSaldo < 0: lançar SaldoInsuficienteException()
     * - Setar novo saldo e salvar
     *
     * IMPORTANTE: Saldo negativo é BLOQUEADO — lança exceção, não apenas alerta.
     *
     * @param contaId   ID da conta
     * @param valor     valor a debitar (positivo)
     * @param usuarioId ID do dono da conta
     */
    @Transactional
    public void debitarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
        ContaEntity conta = buscarContaValidada(contaId, usuarioId);

        if (conta.getSaldo().compareTo(valor) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente");
        }

        conta.setSaldo(conta.getSaldo().subtract(valor));
        contaRepository.save(conta);
    }

    /**
     * Credita um valor ao saldo da conta.
     * Usado por TransacaoService ao criar RECEITA.
     *
     * REGRAS:
     * - Buscar conta por ID e validar que pertence ao usuário + ativa
     * - Se não encontrar: lançar RecursoNaoEncontradoException("Conta não encontrada")
     * - Calcular novoSaldo = saldoAtual + valor
     * - Setar novo saldo e salvar
     *
     * @param contaId   ID da conta
     * @param valor     valor a creditar (positivo)
     * @param usuarioId ID do dono da conta
     */
    @Transactional
    public void creditarSaldo(Long contaId, BigDecimal valor, Long usuarioId) {
        ContaEntity conta = buscarContaValidada(contaId, usuarioId);

        conta.setSaldo(conta.getSaldo().add(valor));
        contaRepository.save(conta);
    }

    /**
     * Desativa (soft delete) uma conta.
     * REGRAS:
     * - Buscar conta por ID e validar que pertence ao usuário + ativa
     * - Se não encontrar: lançar RecursoNaoEncontradoException("Conta não encontrada")
     * - Setar ativo = false
     * - Salvar
     */
    @Transactional
    public void deletarConta(Long contaId, Long usuarioId) {
        ContaEntity conta = buscarContaValidada(contaId, usuarioId);

        if (conta.getSaldo().compareTo(BigDecimal.ZERO) > 0) {
            throw new RegraDeNegocioException("Não é possível inativar uma conta que ainda possui saldo.");
        }

        conta.setAtivo(false);
        contaRepository.save(conta);
    }

    /**
     * Método auxiliar privado para buscar uma conta e validar todas as regras de acesso.
     */
    private ContaEntity buscarContaValidada(Long contaId, Long usuarioId) {
        ContaEntity conta = contaRepository.findById(contaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));

        usuarioService.buscarPorIdOuFalhar(usuarioId);

        if (!conta.getUsuario().getId().equals(usuarioId) || !conta.isAtivo()) {
            throw new RecursoNaoEncontradoException("Conta inativa ou não pertence ao usuário");
        }

        return conta;
    }
}
