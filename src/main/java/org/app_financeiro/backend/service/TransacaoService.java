package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.TransacaoResponseDTO;
import org.app_financeiro.backend.mapper.TransacaoMapper;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.service.MovimentacaoFinanceiraService;
import org.app_financeiro.backend.service.ResultadoMovimentacaoCartao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service responsável pelas transações financeiras (RECEITA e DESPESA).
 *
 * <p>Esta é a classe mais complexa do sistema. Cada transação impacta o saldo de
 * uma conta OU o limite de um cartão, condicionado ao status da transação.</p>
 *
 * <p><b>REGRAS DE IMPACTO FINANCEIRO:</b></p>
 *
 * <p><b>Transações em CONTA</b> (impacto apenas quando status = PAGO):</p>
 * <ul>
 *   <li>DESPESA + conta + PAGO → {@code contaService.debitarSaldo()}</li>
 *   <li>RECEITA + conta + PAGO → {@code contaService.creditarSaldo()}</li>
 *   <li>PENDENTE + conta → nenhum impacto (apenas registra)</li>
 * </ul>
 *
 * <p><b>Transações em CARTÃO</b> (status sempre forçado a PENDENTE):</p>
 * <ul>
 *   <li>DESPESA + cartão → {@code consumirLimite()} + {@code faturaService.adicionarTransacao()}</li>
 *   <li>RECEITA + cartão → Estorno/Cashback: {@code faturaService.registrarCredito()}
 *       (subtrai da fatura; se o valor exceder o valorTotal, clamp para R$ 0,00)</li>
 * </ul>
 *
 * <p>Transações em cartão são sempre PENDENTE (pois o pagamento real ocorre
 * quando a fatura é paga via {@link FaturaService#pagarFatura}).</p>
 *
 * <p><b>DEFINIÇÃO AUTOMÁTICA DE STATUS</b> (quando o usuário não informa):</p>
 * <ul>
 *   <li>Cartão: sempre PENDENTE (forçado, mesmo que o usuário informe outro)</li>
 *   <li>PIX, DINHEIRO, CARTAO_DEBITO, VALE_ALIMENTACAO, TRANSFERENCIA → PAGO</li>
 *   <li>BOLETO, CARTAO_CREDITO, ou método não informado → PENDENTE</li>
 * </ul>
 *
 * <p>Se o usuário informar o status explicitamente, o sistema respeita a escolha
 * (exceto para cartão, que é sempre forçado PENDENTE).</p>
 */
@Service
public class TransacaoService {

    private static final Logger log = LoggerFactory.getLogger(TransacaoService.class);

    private final TransacaoRepository transacaoRepository;
    private final MovimentacaoFinanceiraService movimentacaoFinanceiraService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;
    private final TransacaoMapper transacaoMapper;

    public TransacaoService(TransacaoRepository transacaoRepository,
                            MovimentacaoFinanceiraService movimentacaoFinanceiraService,
                            CategoriaService categoriaService,
                            UsuarioService usuarioService,
                            TransacaoMapper transacaoMapper) {
        this.transacaoRepository = transacaoRepository;
        this.movimentacaoFinanceiraService = movimentacaoFinanceiraService;
        this.categoriaService = categoriaService;
        this.usuarioService = usuarioService;
        this.transacaoMapper = transacaoMapper;
    }

    /**
     * Cria uma nova transação financeira (RECEITA ou DESPESA).
     *
     * <p><b>Fluxo:</b></p>
     * <ol>
     *   <li>Validações fail-fast (contaId XOR cartaoId, categoria compatível com tipo)</li>
     *   <li>Definir status automático via {@link #definirStatus}</li>
     *   <li>Aplicar impacto financeiro (conta ou cartão)</li>
     *   <li>Persistir a transação</li>
     * </ol>
     *
     * @param dto       Dados da transação a criar
     * @param usuarioId ID do usuário autenticado
     * @return TransacaoResponseDTO com os dados da transação criada
     * @throws RegraDeNegocioException       se contaId e cartaoId forem informados ao mesmo tempo ou nenhum
     * @throws RecursoNaoEncontradoException  se conta, cartão ou categoria não existirem
     * @throws SaldoInsuficienteException     se o saldo da conta for insuficiente (DESPESA + conta + PAGO)
     */
    @Transactional
    public TransacaoResponseDTO criarTransacao(TransacaoRegistroRequestDTO dto, Long usuarioId) {

        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);

        if (dto.contaId() != null && dto.cartaoId() != null) {
            throw new RegraDeNegocioException("Não é permitido informar contaId e cartaoId ao mesmo tempo");
        }

        if (dto.contaId() == null && dto.cartaoId() == null) {
            throw new RegraDeNegocioException("Necessário informar contaId ou cartaoId para criar a transação");
        }


        CategoriaEntity categoria = null;
        if (dto.categoriaId() != null) {
            categoria = categoriaService.buscarPorIdOuFalhar(dto.categoriaId(), usuarioId);

            if (categoria.getTipo() != dto.tipo()) {
                throw new RegraDeNegocioException(
                        "Categoria do tipo " + categoria.getTipo() +
                        " não pode ser usada em transação do tipo " + dto.tipo());
            }
        }

        StatusTransacao status = definirStatus(dto);
        validarParcelas(dto);

        ImpactoFinanceiro impactos = processarImpacto(dto, status, usuarioId);

        TransacaoEntity transacao = new TransacaoEntity();
        transacao.setDescricao(dto.descricao());
        transacao.setValor(dto.valor());
        transacao.setData(dto.data());
        transacao.setTipo(dto.tipo());
        transacao.setStatus(status);
        transacao.setMetodoPagamento(dto.metodoPagamento());
        transacao.setUsuario(usuario);
        transacao.setCategoria(categoria);
        transacao.setConta(impactos.conta());
        transacao.setCartao(impactos.cartao());
        transacao.setFatura(impactos.fatura());
        transacao.setNumeroParcela(dto.numeroParcela());
        transacao.setTotalParcelas(dto.totalParcelas());
        transacao.setAtivo(true);

        transacaoRepository.save(transacao);
        log.info("Transação {} criada: {} R$ {} status={} usuário={}", transacao.getId(), dto.tipo(), dto.valor(), status, usuarioId);

        return transacaoMapper.toResponse(transacao);
    }

    /**
     * Atualiza uma transação existente.
     *
     * <p><b>Fluxo em 4 fases:</b></p>
     * <ol>
     *   <li>Reverter impacto financeiro da transação antiga</li>
     *   <li>Validar os novos dados (contaId XOR cartaoId, categoria compatível)</li>
     *   <li>Aplicar impacto financeiro dos novos dados</li>
     *   <li>Atualizar campos da entidade e salvar</li>
     * </ol>
     *
     * @param transacaoId ID da transação a atualizar
     * @param dto         Novos dados da transação
     * @param usuarioId   ID do usuário autenticado
     * @return TransacaoResponseDTO com os dados atualizados
     * @throws RecursoNaoEncontradoException se a transação não existir ou não pertencer ao usuário
     * @throws RegraDeNegocioException       se os dados forem inválidos
     */
    @Transactional
    public TransacaoResponseDTO atualizarTransacao(Long transacaoId, TransacaoRegistroRequestDTO dto, Long usuarioId) {

        TransacaoEntity transacao = transacaoRepository.findById(transacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transação não encontrada"));

        if (!transacao.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Transação não pertence ao usuário");
        }

        movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(transacao, usuarioId);

        if (dto.contaId() != null && dto.cartaoId() != null) {
            throw new RegraDeNegocioException("Não é permitido informar contaId e cartaoId ao mesmo tempo");
        }
        if (dto.contaId() == null && dto.cartaoId() == null) {
            throw new RegraDeNegocioException("Necessário informar contaId ou cartaoId");
        }
        
        CategoriaEntity categoria = null;
        if (dto.categoriaId() != null) {
            categoria = categoriaService.buscarPorIdOuFalhar(dto.categoriaId(), usuarioId);
            if (categoria.getTipo() != dto.tipo()) {
                throw new RegraDeNegocioException("Categoria incompatível com o tipo");
            }
        }

        StatusTransacao novoStatus = definirStatus(dto);
        validarParcelas(dto);

        ImpactoFinanceiro impactos = processarImpacto(dto, novoStatus, usuarioId);

        transacao.setDescricao(dto.descricao());
        transacao.setValor(dto.valor());
        transacao.setData(dto.data());
        transacao.setTipo(dto.tipo());
        transacao.setStatus(novoStatus);
        transacao.setMetodoPagamento(dto.metodoPagamento());
        transacao.setCategoria(categoria);
        transacao.setConta(impactos.conta());
        transacao.setCartao(impactos.cartao());
        transacao.setFatura(impactos.fatura());
        transacao.setNumeroParcela(dto.numeroParcela());
        transacao.setTotalParcelas(dto.totalParcelas());

        transacaoRepository.save(transacao);
        return transacaoMapper.toResponse(transacao);
    }

    /**
     * Desativa (soft delete) uma transação e reverte seu impacto financeiro.
     * Se era DESPESA+conta+PAGO, credita o valor de volta. Se era via cartão, remove da fatura.
     *
     * @param transacaoId ID da transação a deletar
     * @param usuarioId   ID do usuário autenticado
     * @throws RecursoNaoEncontradoException se a transação não existir, não pertencer ao usuário ou já estiver inativa
     */
    @Transactional
    public void deletarTransacao(Long transacaoId, Long usuarioId) {
        
        TransacaoEntity transacao = transacaoRepository.findById(transacaoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Transação não encontrada"));

        if (!transacao.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Transação não pertence ao usuário");
        }

        movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(transacao, usuarioId);

        transacao.setAtivo(false);
        transacaoRepository.save(transacao);
        log.info("Transação {} desativada (soft delete) para usuário {}", transacaoId, usuarioId);
    }

    /**
     * Lista transações ativas do usuário filtradas por mês/ano.
     * Calcula o intervalo de datas [primeiro dia, último dia] do mês informado.
     *
     * @param ano       Ano de referência (ex: 2026)
     * @param mes       Mês de referência (1-12)
     * @param usuarioId ID do usuário autenticado
     * @return Lista de TransacaoResponseDTO (pode ser vazia)
     */
    public List<TransacaoResponseDTO> buscarPorMes(int ano, int mes, Long usuarioId) {
        LocalDate dataInicio = LocalDate.of(ano, mes, 1);
        LocalDate dataFim = dataInicio.withDayOfMonth(dataInicio.lengthOfMonth());

        List<TransacaoEntity> transacoes =
                transacaoRepository.findByUsuarioIdAndDataBetween(usuarioId, dataInicio, dataFim);

        return transacoes.stream()
                .map(transacaoMapper::toResponse)
                .toList();
    }

    /**
     * Lista transações paginadas de um usuário.
     * @param usuarioId ID do usuário
     * @param pageable parâmetros de página e ordenação
     * @return página de DTOs de transação
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TransacaoResponseDTO> listarPorUsuario(Long usuarioId,
                                                                                         org.springframework.data.domain.Pageable pageable) {
        return transacaoRepository.findByUsuarioId(usuarioId, pageable)
                .map(transacaoMapper::toResponse);
    }

    /**
     * Valida a consistência dos campos de parcelamento.
     * Se ambos forem informados, o número da parcela não pode ser maior que o total.
     *
     * @param dto Dados da transação com campos de parcelamento opcionais
     * @throws RegraDeNegocioException se numeroParcela > totalParcelas ou totalParcelas < 1
     */
    private void validarParcelas(TransacaoRegistroRequestDTO dto) {
        if (dto.numeroParcela() != null && dto.totalParcelas() != null) {
            if (dto.numeroParcela() > dto.totalParcelas()) {
                throw new RegraDeNegocioException("O número da parcela não pode ser maior que o total de parcelas");
            }
        }
        
        if (dto.totalParcelas() != null && dto.totalParcelas() < 1) {
            throw new RegraDeNegocioException("O total de parcelas deve ser no mínimo 1");
        }
    }

    private StatusTransacao definirStatus(TransacaoRegistroRequestDTO dto) {
        if (dto.cartaoId() != null) {
            return StatusTransacao.PENDENTE;
        }

        if (dto.status() != null) {
            return dto.status();
        }

        MetodoPagamento metodo = dto.metodoPagamento();
        if (metodo == null) {
            return StatusTransacao.PENDENTE;
        }

        return switch (metodo) {
            case PIX, DINHEIRO, CARTAO_DEBITO, VALE_ALIMENTACAO, TRANSFERENCIA -> StatusTransacao.PAGO;
            case BOLETO, CARTAO_CREDITO -> StatusTransacao.PENDENTE;
        };
    }

    private ImpactoFinanceiro processarImpacto(TransacaoRegistroRequestDTO dto, StatusTransacao status, Long usuarioId) {
        ContaEntity conta = null;
        CartaoEntity cartao = null;
        FaturaEntity fatura = null;

        if (dto.contaId() != null) {
            conta = movimentacaoFinanceiraService.processarTransacaoConta(
                    dto.tipo(), status, dto.contaId(), dto.valor(), usuarioId);
        } else {
            if (dto.tipo() == TipoTransacao.DESPESA) {
                ResultadoMovimentacaoCartao res = movimentacaoFinanceiraService.processarDespesaCartao(
                        dto.cartaoId(), dto.data(), dto.valor(), usuarioId);
                cartao = res.cartao();
                fatura = res.fatura();
            } else {
                ResultadoMovimentacaoCartao res = movimentacaoFinanceiraService.processarEstornoCartao(
                        dto.cartaoId(), dto.data(), dto.valor(), usuarioId);
                cartao = res.cartao();
                fatura = res.fatura();
            }
        }
        return new ImpactoFinanceiro(conta, cartao, fatura);
    }

    private record ImpactoFinanceiro(ContaEntity conta, CartaoEntity cartao, FaturaEntity fatura) {}
}
