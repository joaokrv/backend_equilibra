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
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.util.ValidacaoRecursoUtil;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.OperacaoNaoPermitidaException;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.repository.TransacaoRecorrenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Gerencia transações financeiras e seus impactos em contas e cartões. */
@Service
public class TransacaoService {

    private static final Logger log = LoggerFactory.getLogger(TransacaoService.class);

    private final TransacaoRepository transacaoRepository;
    private final TransacaoRecorrenteRepository transacaoRecorrenteRepository;
    private final MovimentacaoFinanceiraService movimentacaoFinanceiraService;
    private final CategoriaService categoriaService;
    private final UsuarioService usuarioService;
    private final TransacaoMapper transacaoMapper;
    private final FaturaService faturaService;

    public TransacaoService(TransacaoRepository transacaoRepository,
                            TransacaoRecorrenteRepository transacaoRecorrenteRepository,
                            MovimentacaoFinanceiraService movimentacaoFinanceiraService,
                            CategoriaService categoriaService,
                            UsuarioService usuarioService,
                            TransacaoMapper transacaoMapper,
                            FaturaService faturaService) {
        this.transacaoRepository = transacaoRepository;
        this.transacaoRecorrenteRepository = transacaoRecorrenteRepository;
        this.movimentacaoFinanceiraService = movimentacaoFinanceiraService;
        this.categoriaService = categoriaService;
        this.usuarioService = usuarioService;
        this.transacaoMapper = transacaoMapper;
        this.faturaService = faturaService;
    }

    @Transactional
    public TransacaoResponseDTO criarTransacao(TransacaoRegistroRequestDTO dto, Long usuarioId) {
        return criarTransacaoInterna(dto, usuarioId, false);
    }

    TransacaoResponseDTO criarTransacaoInterna(TransacaoRegistroRequestDTO dto, Long usuarioId, boolean transferencia) {
        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);

        if (transacaoRepository.existsByIdempotencyKey(dto.idempotencyKey())) {
            log.warn("Tentativa de criação de transação duplicada detectada: idempotencyKey={}", dto.idempotencyKey());
            throw new OperacaoNaoPermitidaException("Esta transação já foi processada anteriormente.");
        }

        ValidacaoRecursoUtil.validarContaXorCartao(dto.contaId(), dto.cartaoId());

        CategoriaEntity categoria = null;
        if (dto.categoriaId() != null) {
            categoria = categoriaService.buscarPorIdOuFalhar(dto.categoriaId(), usuarioId);

            if (categoria.getTipo() != dto.tipo()) {
                throw new RegraDeNegocioException("error.transacao.categoria_incompativel",
                                                 "Categoria do tipo " + categoria.getTipo() + " não pode ser usada em transação do tipo " + dto.tipo(),
                                                 categoria.getTipo(), dto.tipo());
            }
        }

        StatusTransacao status = definirStatus(dto);
        validarParcelas(dto);

        if (isCompraParcelada(dto)) {
            return criarCompraParcelada(dto, usuario, categoria, usuarioId, transferencia);
        }

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
        transacao.setTransferencia(transferencia);

        if (dto.recorrenteId() != null) {
            Long recorrenteId = Objects.requireNonNull(dto.recorrenteId());
            transacao.setRecorrente(
                transacaoRecorrenteRepository.findByIdAndUsuarioId(recorrenteId, usuarioId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Recorrência não encontrada"))
            );
        }

        transacaoRepository.save(transacao);
        log.info("Transação {} criada para usuário {}", transacao.getId(), usuarioId);

        return transacaoMapper.toResponse(transacao);
    }

    @Transactional
    public TransacaoResponseDTO atualizarTransacao(Long transacaoId, TransacaoRegistroRequestDTO dto, Long usuarioId) {
        if (transacaoId == null || usuarioId == null) {
            throw new RegraDeNegocioException("error.transacao.id_nulo", "ID de transação ou usuário não pode ser nulo");
        }
        TransacaoEntity transacao = transacaoRepository.findById(transacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transação não encontrada"));

        if (!transacao.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Transação não pertence ao usuário");
        }

        if (transacao.getGrupoParcelamento() != null
                && (transacao.getValor().compareTo(dto.valor()) != 0 || !transacao.getData().equals(dto.data()))) {
            throw new RegraDeNegocioException("error.transacao.parcela_imutavel",
                    "Não é possível alterar valor ou data de uma parcela isolada. Exclua a compra parcelada e recrie.");
        }

        movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(transacao, usuarioId);

        ValidacaoRecursoUtil.validarContaXorCartao(dto.contaId(), dto.cartaoId());

        CategoriaEntity categoria = null;
        if (dto.categoriaId() != null) {
            categoria = categoriaService.buscarPorIdOuFalhar(dto.categoriaId(), usuarioId);
            if (categoria.getTipo() != dto.tipo()) {
                throw new RegraDeNegocioException("error.transacao.categoria_incompativel_curta", "Categoria incompatível com o tipo");
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

        if (transacao.getGrupoParcelamento() != null) {
            propagarMetadadosParaGrupo(transacao, categoria);
        }

        return transacaoMapper.toResponse(transacao);
    }

    /** Propaga descrição e categoria para as demais parcelas do mesmo grupo, sem alterar valores/datas. */
    private void propagarMetadadosParaGrupo(TransacaoEntity origem, CategoriaEntity categoria) {
        List<TransacaoEntity> parcelas = transacaoRepository
                .findByGrupoParcelamentoAndUsuarioId(origem.getGrupoParcelamento(), origem.getUsuario().getId());
        for (TransacaoEntity parcela : parcelas) {
            if (parcela.getId().equals(origem.getId())) {
                continue;
            }
            parcela.setDescricao(origem.getDescricao());
            parcela.setCategoria(categoria);
            transacaoRepository.save(parcela);
        }
    }

    @Transactional
    public void deletarTransacao(Long transacaoId, Long usuarioId) {
        deletarTransacao(transacaoId, usuarioId, false);
    }

    @Transactional
    public void deletarTransacao(Long transacaoId, Long usuarioId, boolean grupoCompleto) {
        if (transacaoId == null || usuarioId == null) {
            throw new RegraDeNegocioException("error.transacao.id_nulo", "ID de transação ou usuário não pode ser nulo");
        }
        TransacaoEntity transacao = transacaoRepository.findById(transacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transação não encontrada"));

        if (!transacao.getUsuario().getId().equals(usuarioId)) {
            throw new RecursoNaoEncontradoException("Transação não pertence ao usuário");
        }

        if (grupoCompleto && transacao.getGrupoParcelamento() != null) {
            List<TransacaoEntity> parcelas = transacaoRepository
                    .findByGrupoParcelamentoAndUsuarioId(transacao.getGrupoParcelamento(), usuarioId);
            parcelas.forEach(this::validarFaturaNaoPaga);
            for (TransacaoEntity parcela : parcelas) {
                movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(parcela, usuarioId);
                parcela.setAtivo(false);
                transacaoRepository.save(parcela);
            }
            log.info("Compra parcelada (grupo {}) excluída: {} parcela(s) para usuário {}",
                    transacao.getGrupoParcelamento(), parcelas.size(), usuarioId);
            return;
        }

        validarFaturaNaoPaga(transacao);
        movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(transacao, usuarioId);

        transacao.setAtivo(false);
        transacaoRepository.save(transacao);
        log.info("Transação {} desativada (soft delete) para usuário {}", transacaoId, usuarioId);
    }

    /** Impede reverter o impacto de uma transação cuja fatura já foi quitada (evita valorPago > valorTotal). */
    private void validarFaturaNaoPaga(TransacaoEntity transacao) {
        if (transacao.getFatura() != null && transacao.getFatura().getStatus() == StatusFatura.PAGA) {
            throw new RegraDeNegocioException("error.transacao.fatura_paga", "Não é possível excluir uma transação cuja fatura já foi paga.");
        }
    }

    public List<TransacaoResponseDTO> buscarPorMes(int ano, int mes, Long usuarioId) {
        LocalDate dataInicio = LocalDate.of(ano, mes, 1);
        LocalDate dataFim = dataInicio.withDayOfMonth(dataInicio.lengthOfMonth());

        List<TransacaoEntity> transacoes =
                transacaoRepository.findByUsuarioIdAndDataBetween(usuarioId, dataInicio, dataFim);

        return transacoes.stream()
                .map(transacaoMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransacaoResponseDTO> listarPorIntervalo(LocalDate dataInicio, LocalDate dataFim, Long usuarioId) {
        if (dataFim.isBefore(dataInicio)) {
            throw new RegraDeNegocioException("error.intervalo.data_fim_anterior", "dataFim nao pode ser anterior a dataInicio");
        }
        if (dataInicio.until(dataFim).toTotalMonths() > 12) {
            throw new RegraDeNegocioException("error.intervalo.maximo_12_meses", "Intervalo maximo permitido e de 12 meses");
        }

        List<TransacaoEntity> transacoes =
                transacaoRepository.findByUsuarioIdAndDataBetween(usuarioId, dataInicio, dataFim);

        return transacoes.stream()
                .sorted((a, b) -> b.getData().compareTo(a.getData()))
                .map(transacaoMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TransacaoResponseDTO> listarPorUsuario(Long usuarioId, Pageable pageable) {
        return transacaoRepository.findByUsuarioId(usuarioId, pageable)
                .map(transacaoMapper::toResponse);
    }

    @Transactional
    public List<TransacaoResponseDTO> buscarPorFatura(Long faturaId, Long usuarioId) {
        faturaService.buscarFaturaComDetalhe(faturaId, usuarioId);
        List<TransacaoEntity> transacoes = transacaoRepository.findByFaturaId(faturaId);
        return transacoes.stream()
                .map(transacaoMapper::toResponse)
                .toList();
    }

    private void validarParcelas(TransacaoRegistroRequestDTO dto) {
        if (dto.numeroParcela() != null && dto.totalParcelas() != null) {
            if (dto.numeroParcela() > dto.totalParcelas()) {
                throw new RegraDeNegocioException("error.parcela.numero_maior_que_total", "O número da parcela não pode ser maior que o total de parcelas");
            }
        }

        if (dto.totalParcelas() != null && dto.totalParcelas() < 1) {
            throw new RegraDeNegocioException("error.parcela.minimo_1", "O total de parcelas deve ser no mínimo 1");
        }

        if (dto.totalParcelas() != null && dto.totalParcelas() > 72) {
            throw new RegraDeNegocioException("error.parcela.maximo_72", "O total de parcelas não pode exceder 72");
        }

        if (dto.totalParcelas() != null && dto.totalParcelas() > 1) {
            if (dto.cartaoId() == null) {
                throw new RegraDeNegocioException("error.parcela.somente_cartao", "Parcelamento só é permitido em despesas de cartão de crédito.");
            }
            if (dto.tipo() != TipoTransacao.DESPESA) {
                throw new RegraDeNegocioException("error.parcela.somente_despesa", "Parcelamento só é permitido em despesas.");
            }
        }
    }

    private boolean isCompraParcelada(TransacaoRegistroRequestDTO dto) {
        return dto.cartaoId() != null
                && dto.totalParcelas() != null
                && dto.totalParcelas() > 1
                && dto.tipo() == TipoTransacao.DESPESA;
    }

    /**
     * Gera N transações (uma por parcela) a partir de uma única compra parcelada no cartão.
     * O valor é rateado (resíduo absorvido pela última parcela) e cada parcela é lançada na
     * fatura do mês correspondente. O limite é validado incrementalmente por {@code consumirLimite}
     * dentro da mesma transação — se o total exceder o limite, todo o lançamento sofre rollback.
     */
    private TransacaoResponseDTO criarCompraParcelada(TransacaoRegistroRequestDTO dto, UsuarioEntity usuario,
                                                      CategoriaEntity categoria, Long usuarioId, boolean transferencia) {
        int totalParcelas = dto.totalParcelas();

        if (transacaoRepository.existsByIdempotencyKey(dto.idempotencyKey() + "-p1")) {
            log.warn("Tentativa de criação de compra parcelada duplicada: idempotencyKey={}", dto.idempotencyKey());
            throw new OperacaoNaoPermitidaException("Esta transação já foi processada anteriormente.");
        }

        UUID grupo = UUID.randomUUID();
        BigDecimal valorParcela = dto.valor().divide(BigDecimal.valueOf(totalParcelas), 2, RoundingMode.HALF_EVEN);
        BigDecimal valorUltimaParcela = dto.valor().subtract(valorParcela.multiply(BigDecimal.valueOf(totalParcelas - 1L)));

        TransacaoEntity primeira = null;
        for (int i = 1; i <= totalParcelas; i++) {
            BigDecimal valor = (i < totalParcelas) ? valorParcela : valorUltimaParcela;
            LocalDate dataParcela = dto.data().plusMonths(i - 1L);

            ResultadoMovimentacaoCartao res = movimentacaoFinanceiraService.processarDespesaCartao(
                    dto.cartaoId(), dataParcela, valor, usuarioId);

            TransacaoEntity parcela = new TransacaoEntity();
            parcela.setDescricao(dto.descricao());
            parcela.setValor(valor);
            parcela.setData(dataParcela);
            parcela.setTipo(dto.tipo());
            parcela.setStatus(StatusTransacao.PENDENTE);
            parcela.setMetodoPagamento(dto.metodoPagamento());
            parcela.setUsuario(usuario);
            parcela.setCategoria(categoria);
            parcela.setCartao(res.cartao());
            parcela.setFatura(res.fatura());
            parcela.setNumeroParcela(i);
            parcela.setTotalParcelas(totalParcelas);
            parcela.setGrupoParcelamento(grupo);
            parcela.setAtivo(true);
            parcela.setIdempotencyKey(dto.idempotencyKey() + "-p" + i);
            parcela.setTransferencia(transferencia);

            transacaoRepository.save(parcela);
            if (i == 1) {
                primeira = parcela;
            }
        }

        log.info("Compra parcelada criada: grupo={}, parcelas={}, total=R$ {}, usuario={}",
                grupo, totalParcelas, dto.valor(), usuarioId);
        return transacaoMapper.toResponse(primeira);
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
            log.warn("Transação sem metodoPagamento. descricao={}, tipo={}, valor={}, contaId={}, cartaoId={}, recorrenteId={}",
                    dto.descricao(), dto.tipo(), dto.valor(), dto.contaId(), dto.cartaoId(), dto.recorrenteId());
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
