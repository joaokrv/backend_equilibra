package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.TransacaoResponseDTO;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service responsável pelas transações financeiras (RECEITA e DESPESA).
 *
 * Esta é a classe mais complexa do sistema. Cada transação impacta o saldo de
 * uma conta OU o limite de um cartão. As regras de impacto são:
 *
 * CRIAR TRANSAÇÃO:
 * - DESPESA + conta  → contaService.debitarSaldo() (bloqueia saldo negativo)
 * - DESPESA + cartão → cartaoService.consumirLimite() (bloqueia limite insuficiente)
 * - RECEITA + conta  → contaService.creditarSaldo()
 * - RECEITA + cartão → não faz sentido (receita não entra no cartão)
 *
 * DELETAR TRANSAÇÃO (reverter impacto):
 * - DESPESA + conta  → contaService.creditarSaldo() (devolver o saldo)
 * - DESPESA + cartão → cartaoService.restaurarLimite() (devolver o limite)
 * - RECEITA + conta  → contaService.debitarSaldo() (remover o crédito)
 *
 * ATUALIZAR TRANSAÇÃO:
 * - Reverter impacto da transação antiga
 * - Aplicar impacto da transação nova
 */
@Service
public class TransacaoService {

    private final TransacaoRepository transacaoRepository;
    private final ContaService contaService;
    private final CartaoService cartaoService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;

    public TransacaoService(TransacaoRepository transacaoRepository,
                            ContaService contaService,
                            CartaoService cartaoService,
                            CategoriaService categoriaService,
                            UsuarioService usuarioService) {
        this.transacaoRepository = transacaoRepository;
        this.contaService = contaService;
        this.cartaoService = cartaoService;
        this.categoriaService = categoriaService;
        this.usuarioService = usuarioService;
    }

    /**
     * Cria uma nova transação financeira.
     *
     * REGRAS que você deve implementar:
     * 1. Validar que o usuário existe (usuarioService.buscarPorIdOuFalhar)
     * 2. Validar entidades relacionadas:
     *    - Se categoriaId != null → buscar categoria (categoriaService.buscarPorIdOuFalhar)
     *    - Se contaId != null → buscar conta (usar contaRepository ou contaService)
     *    - Se cartaoId != null → buscar cartão (usar cartaoRepository ou cartaoService)
     * 3. Validar que não informou contaId E cartaoId ao mesmo tempo
     *    (lançar RegraDeNegocioException se ambos vierem preenchidos)
     * 4. Se status vier null → default StatusTransacao.PENDENTE
     * 5. Aplicar impacto financeiro:
     *    - DESPESA + contaId → contaService.debitarSaldo(contaId, valor, usuarioId)
     *    - DESPESA + cartaoId → cartaoService.consumirLimite(cartaoId, valor, usuarioId)
     *    - RECEITA + contaId → contaService.creditarSaldo(contaId, valor, usuarioId)
     *    - RECEITA + cartaoId → lançar RegraDeNegocioException("Receita não pode ser vinculada a cartão")
     * 6. Criar TransacaoEntity, setar todos os campos, salvar
     * 7. Retornar TransacaoResponseDTO
     */
    @Transactional
    public TransacaoResponseDTO criarTransacao(TransacaoRegistroRequestDTO dto, Long usuarioId) {
        // TODO: Implementar - esta é a regra mais complexa do sistema. Siga os passos acima.
        return null;
    }

    /**
     * Atualiza uma transação existente.
     *
     * REGRAS que você deve implementar:
     * 1. Buscar a transação por ID + validar que pertence ao usuário + ativa
     *    (lançar RecursoNaoEncontradoException se não encontrar)
     * 2. REVERTER o impacto financeiro da transação ANTIGA:
     *    - Se era DESPESA + conta → creditarSaldo (devolver)
     *    - Se era DESPESA + cartão → restaurarLimite (devolver)
     *    - Se era RECEITA + conta → debitarSaldo (remover)
     * 3. Validar novas entidades (categoriaId, contaId, cartaoId)
     * 4. APLICAR o impacto financeiro da transação NOVA (mesma lógica do criar)
     * 5. Atualizar os campos da entity e salvar
     * 6. Retornar TransacaoResponseDTO
     *
     * DICA: Considere extrair métodos privados como aplicarImpacto() e reverterImpacto()
     * para reusar entre criar, atualizar e deletar.
     */
    @Transactional
    public TransacaoResponseDTO atualizarTransacao(Long transacaoId, TransacaoRegistroRequestDTO novaTransacao, Long usuarioId) {
        // TODO: Implementar - reverter impacto antigo, aplicar novo impacto
        return null;
    }

    /**
     * Desativa (soft delete) uma transação e reverte seu impacto financeiro.
     *
     * REGRAS que você deve implementar:
     * 1. Buscar a transação por ID + validar que pertence ao usuário + ativa
     *    (lançar RecursoNaoEncontradoException se não encontrar)
     * 2. REVERTER o impacto financeiro:
     *    - Se era DESPESA + conta → creditarSaldo (devolver)
     *    - Se era DESPESA + cartão → restaurarLimite (devolver)
     *    - Se era RECEITA + conta → debitarSaldo (remover)
     * 3. Setar ativo = false
     * 4. Salvar
     */
    @Transactional
    public void deletarTransacao(Long transacaoId, Long usuarioId) {
        // TODO: Implementar - reverter impacto e soft delete
    }

    /**
     * Lista transações do usuário filtradas por mês/ano.
     *
     * REGRAS que você deve implementar:
     * - Calcular dataInicio = primeiro dia do mês (LocalDate.of(ano, mes, 1))
     * - Calcular dataFim = último dia do mês (dataInicio.withDayOfMonth(dataInicio.lengthOfMonth()))
     * - Usar transacaoRepository.findByUsuarioIdAndDataBetweenAndAtivoTrue(usuarioId, dataInicio, dataFim)
     * - Converter para TransacaoResponseDTO
     * - Retornar a lista
     */
    public List<TransacaoResponseDTO> buscarPorMes(int ano, int mes, Long usuarioId) {
        // TODO: Implementar - calcular range de datas, buscar, converter
        return null;
    }
}
