package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.model.ResultadoMovimentacaoCartao;
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
import org.app_financeiro.backend.exception.OperacaoNaoPermitidaException;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service responsável pelas transações financeiras (RECEITA e DESPESA).
 * Gerencia o impacto no saldo de contas bancárias e limite de cartões de crédito.
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
     * Cria uma nova transação financeira registrando os impactos nas contas ou cartões.
     *
     * @param dto dados da transação
     * @param usuarioId ID do usuário autenticado
     * @return DTO com os dados da transação criada
     * @throws RegraDeNegocioException caso as regras de vínculo de conta/cartão sejam violadas
     * @throws OperacaoNaoPermitidaException caso detectada transação duplicada (idempotency)
     */
    @Transactional
    public TransacaoResponseDTO criarTransacao(TransacaoRegistroRequestDTO dto, Long usuarioId) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);

        if (transacaoRepository.existsByIdempotencyKey(dto.idempotencyKey())) {
            log.warn("Tentativa de criação de transação duplicada detectada: idempotencyKey={}", dto.idempotencyKey());
            throw new OperacaoNaoPermitidaException("Esta transação já foi processada anteriormente.");
        }

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
                throw new RegraDeNegocioException("Categoria do tipo " + categoria.getTipo() +
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
        transacao.setIdempotencyKey(dto.idempotencyKey());

        transacaoRepository.save(transacao);
        log.info("Transação {} criada para usuário {}", transacao.getId(), usuarioId);

        return transacaoMapper.toResponse(transacao);
    }

    /**
     * Atualiza os dados de uma transação existente, revertendo efeitos financeiros antigos.
     *
     * @param transacaoId ID da transação a atualizar
     * @param dto novos dados da transação
     * @param usuarioId ID do usuário autenticado
     * @return DTO com os dados atualizados
     */
    @Transactional
    public TransacaoResponseDTO atualizarTransacao(Long transacaoId, TransacaoRegistroRequestDTO dto, Long usuarioId) {
        if (transacaoId == null || usuarioId == null) {
            throw new RegraDeNegocioException("ID de transação ou usuário não pode ser nulo");
        }
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
     * Remove uma transação (Soft Delete) e reverte seu impacto financeiro nas contas/cartões.
     *
     * @param transacaoId ID da transação
     * @param usuarioId ID do usuário autenticado
     */
    @Transactional
    public void deletarTransacao(Long transacaoId, Long usuarioId) {
        if (transacaoId == null || usuarioId == null) {
            throw new RegraDeNegocioException("ID de transação ou usuário não pode ser nulo");
        }
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
     * Busca transações ativas por período de mês e ano.
     *
     * @param ano ano de referência
     * @param mes mês de referência
     * @param usuarioId ID do usuário
     * @return lista de transações localizadas
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
     * Lista transacoes de um usuario em um intervalo de datas.
     *
     * @param dataInicio inicio do intervalo (inclusivo)
     * @param dataFim fim do intervalo (inclusivo)
     * @param usuarioId ID do usuario
     * @return lista de transacoes ordenadas por data desc
     */
    @Transactional(readOnly = true)
    public List<TransacaoResponseDTO> listarPorIntervalo(LocalDate dataInicio, LocalDate dataFim, Long usuarioId) {
        if (dataFim.isBefore(dataInicio)) {
            throw new RegraDeNegocioException("dataFim nao pode ser anterior a dataInicio");
        }
        if (dataInicio.until(dataFim).toTotalMonths() > 12) {
            throw new RegraDeNegocioException("Intervalo maximo permitido e de 12 meses");
        }

        List<TransacaoEntity> transacoes =
                transacaoRepository.findByUsuarioIdAndDataBetween(usuarioId, dataInicio, dataFim);

        return transacoes.stream()
                .sorted((a, b) -> b.getData().compareTo(a.getData()))
                .map(transacaoMapper::toResponse)
                .toList();
    }

    /**
     * Lista transações paginadas de um usuário.
     *
     * @param usuarioId ID do usuário
     * @param pageable parâmetros de paginação
     * @return página de transações processada
     */
    @Transactional(readOnly = true)
    public Page<TransacaoResponseDTO> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return transacaoRepository.findByUsuarioId(usuarioId, pageable)
                .map(transacaoMapper::toResponse);
    }

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
