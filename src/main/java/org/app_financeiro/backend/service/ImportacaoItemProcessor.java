package org.app_financeiro.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.AjusteLinhaDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.VinculoInvestimentoDTO;
import org.app_financeiro.backend.dto.importacao.TransacaoCandidataDTO;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.ClassificacaoCandidata;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.util.FaturaDateUtil;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Processa uma candidata individual em transação PRÓPRIA (REQUIRES_NEW).
 * Isolado do ImportacaoService para que a falha de um item não marque
 * rollback-only a transação do lote — cada item commita ou reverte sozinho,
 * e o resultado parcial (criadas/erros) reportado é fiel ao que foi persistido.
 */
@Slf4j
@Component
public class ImportacaoItemProcessor {

    private static final String ORIGEM_IMPORTACAO = "IMPORTACAO";

    private final TransacaoService transacaoService;
    private final TransacaoRepository transacaoRepository;
    private final MovimentacaoFinanceiraService movimentacaoFinanceiraService;
    private final UsuarioService usuarioService;
    private final InvestimentoService investimentoService;
    private final CartaoService cartaoService;
    private final CategoriaService categoriaService;

    public ImportacaoItemProcessor(TransacaoService transacaoService,
                                   TransacaoRepository transacaoRepository,
                                   MovimentacaoFinanceiraService movimentacaoFinanceiraService,
                                   UsuarioService usuarioService,
                                   InvestimentoService investimentoService,
                                   CartaoService cartaoService,
                                   CategoriaService categoriaService) {
        this.transacaoService = transacaoService;
        this.transacaoRepository = transacaoRepository;
        this.movimentacaoFinanceiraService = movimentacaoFinanceiraService;
        this.usuarioService = usuarioService;
        this.investimentoService = investimentoService;
        this.cartaoService = cartaoService;
        this.categoriaService = categoriaService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processar(TransacaoCandidataDTO candidata,
                          ConfirmarImportacaoRequestDTO dto,
                          Long usuarioId,
                          UUID importacaoId) {
        // APORTE/RESGATE exige vínculo explícito — nunca cai silenciosamente como despesa/receita comum.
        if (candidata.classificacao() == ClassificacaoCandidata.APORTE
                || candidata.classificacao() == ClassificacaoCandidata.RESGATE) {
            processarInvestimento(candidata, dto, usuarioId, importacaoId);
            return;
        }

        // Transferência interna é decisão explícita do usuário no confirm (indicesTransferencia),
        // independente da classificação sugerida — a sugestão nunca decide sozinha.
        if (dto.indicesTransferencia().contains(candidata.indice())) {
            processarTransferenciaInterna(candidata, dto, usuarioId, importacaoId);
            return;
        }

        if (isCartaoHistorico(candidata, dto, usuarioId)) {
            criarTransacaoCartaoHistorico(candidata, dto, usuarioId, importacaoId);
        } else {
            criarTransacaoViaServico(candidata, dto, usuarioId, importacaoId);
        }
    }

    /**
     * Rota histórica é exclusiva de DESPESA (estornos seguem o crédito normal em fatura) cuja
     * fatura de REFERÊNCIA já venceu. O mês de referência (não o civil) decide: compra no dia
     * do fechamento ou depois pertence à fatura do mês seguinte — uma compra do fim do mês
     * passado pode pertencer à fatura corrente e DEVE passar por consumirLimite.
     */
    private boolean isCartaoHistorico(TransacaoCandidataDTO candidata,
                                      ConfirmarImportacaoRequestDTO dto,
                                      Long usuarioId) {
        if (dto.cartaoId() == null || candidata.tipo() != TipoTransacao.DESPESA || candidata.data() == null) {
            return false;
        }
        CartaoEntity cartao = cartaoService.buscarCartaoValidado(dto.cartaoId(), usuarioId);
        YearMonth referencia = FaturaDateUtil.mesReferencia(candidata.data(), cartao.getDiaFechamento());
        return referencia.isBefore(YearMonth.now());
    }

    /** Aporte/resgate sem vínculo é erro de item (contabilizado em "erros" pelo chamador), nunca despesa/receita comum. */
    private void processarInvestimento(TransacaoCandidataDTO candidata,
                                       ConfirmarImportacaoRequestDTO dto,
                                       Long usuarioId,
                                       UUID importacaoId) {
        VinculoInvestimentoDTO vinculo = dto.vinculosInvestimento().stream()
                .filter(v -> v.indice() == candidata.indice())
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException(
                        "error.importacao.vinculo_obrigatorio",
                        "Esta candidata foi classificada como aporte/resgate e precisa de um investimento vinculado."));

        String key = gerarIdempotencyKey(candidata, importacaoId);
        Long transacaoId = candidata.classificacao() == ClassificacaoCandidata.APORTE
                ? investimentoService.aportarImportado(vinculo.investimentoId(), candidata.valor(), dto.contaId(),
                        candidata.data(), key, vinculo.atualizarValor(), usuarioId)
                : investimentoService.resgatarImportado(vinculo.investimentoId(), candidata.valor(), dto.contaId(),
                        candidata.data(), key, vinculo.atualizarValor(), usuarioId);

        marcarComoImportada(transacaoId, importacaoId, null, null);
    }

    /**
     * Marca origem/importacaoId na transação recém-criada, na mesma transação REQUIRES_NEW.
     * Sem isto o desfazer da importação (que busca por importacaoId) nunca a encontraria.
     * Fica como pós-processamento de propósito: os serviços de criação não devem conhecer importação.
     */
    private void marcarComoImportada(Long transacaoId, UUID importacaoId,
                                     Integer numeroParcela, Integer totalParcelas) {
        transacaoRepository.findById(transacaoId).ifPresent(t -> {
            t.setOrigem(ORIGEM_IMPORTACAO);
            t.setImportacaoId(importacaoId);
            if (numeroParcela != null) t.setNumeroParcela(numeroParcela);
            if (totalParcelas != null) t.setTotalParcelas(totalParcelas);
            transacaoRepository.save(t);
        });
    }

    /** Movimentação entre contas do próprio usuário: fora de receitas/despesas (isTransferencia=true), saldo sempre ajustado. */
    private void processarTransferenciaInterna(TransacaoCandidataDTO candidata,
                                               ConfirmarImportacaoRequestDTO dto,
                                               Long usuarioId,
                                               UUID importacaoId) {
        String key = gerarIdempotencyKey(candidata, importacaoId);
        MetodoPagamento metodo = candidata.metodoPagamento() != null
                ? candidata.metodoPagamento()
                : MetodoPagamento.TRANSFERENCIA;

        TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                candidata.descricao(), candidata.valor(), candidata.data(),
                candidata.tipo(), null, metodo,
                dto.contaId(), dto.cartaoId(), null, null,
                null, null, key);

        var resp = transacaoService.criarTransacaoInterna(request, usuarioId, true);
        marcarComoImportada(resp.id(), importacaoId, null, null);
    }

    private void criarTransacaoViaServico(TransacaoCandidataDTO candidata,
                                          ConfirmarImportacaoRequestDTO dto,
                                          Long usuarioId,
                                          UUID importacaoId) {
        String key = gerarIdempotencyKey(candidata, importacaoId);
        Optional<AjusteLinhaDTO> ajuste = buscarAjuste(dto, candidata.indice());
        Long categoriaId = ajuste.map(AjusteLinhaDTO::categoriaId).orElse(null);

        // Cartão: linha de fatura é crédito por natureza, o método detectado/override não se aplica —
        // mas categoria continua livre (compra de cartão tem categoria normalmente).
        // Conta: override do usuário tem prioridade sobre o detectado no documento; status PAGO forçado
        // porque um extrato lista dinheiro que JÁ se movimentou — sem isso, "Boleto" viraria PENDENTE
        // e não debitaria o saldo. Validação de categoria (IDOR + compatibilidade de tipo) é feita
        // por TransacaoService.criarTransacao — categoria inválida vira erro do item, não do lote.
        MetodoPagamento metodo;
        StatusTransacao status;
        if (dto.cartaoId() != null) {
            metodo = MetodoPagamento.CARTAO_CREDITO;
            status = null; // definirStatus → PENDENTE (compra no cartão pendente até pagar a fatura)
        } else {
            metodo = ajuste.map(AjusteLinhaDTO::metodoPagamento).orElse(candidata.metodoPagamento());
            if (metodo == null) metodo = MetodoPagamento.PIX;
            status = StatusTransacao.PAGO;
        }

        // Parcelas do documento são apenas informativas: cada linha da fatura já é a
        // cobrança individual do mês. Passar totalParcelas ao criarTransacao dispararia
        // criarCompraParcelada e explodiria a linha em N transações rateadas.
        TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                candidata.descricao(), candidata.valor(), candidata.data(),
                candidata.tipo(), status, metodo,
                dto.contaId(), dto.cartaoId(), categoriaId, null,
                null, null, key);

        var resp = transacaoService.criarTransacao(request, usuarioId);
        marcarComoImportada(resp.id(), importacaoId, candidata.numeroParcela(), candidata.totalParcelas());
    }

    private Optional<AjusteLinhaDTO> buscarAjuste(ConfirmarImportacaoRequestDTO dto, int indice) {
        return dto.ajustesLinha().stream().filter(a -> a.indice() == indice).findFirst();
    }

    /**
     * Cria transação de cartão em período passado bypassando consumirLimite.
     * A fatura histórica correspondente já foi pré-criada como PAGA (quitacao_historica).
     */
    private void criarTransacaoCartaoHistorico(TransacaoCandidataDTO candidata,
                                               ConfirmarImportacaoRequestDTO dto,
                                               Long usuarioId,
                                               UUID importacaoId) {
        String key = gerarIdempotencyKey(candidata, importacaoId);
        if (transacaoRepository.existsByIdempotencyKey(key)) {
            log.warn("Transação histórica já existente: key={}", key);
            return;
        }

        // Caminho manual: não passa por TransacaoService, então a validação de categoria
        // (IDOR + compatibilidade de tipo) precisa ser feita aqui explicitamente — antes de
        // mutar fatura/limite, para não gastar efeito financeiro numa candidata rejeitável.
        Long categoriaId = buscarAjuste(dto, candidata.indice()).map(AjusteLinhaDTO::categoriaId).orElse(null);
        CategoriaEntity categoria = null;
        if (categoriaId != null) {
            categoria = categoriaService.buscarPorIdOuFalhar(categoriaId, usuarioId);
            if (categoria.getTipo() != TipoTransacao.DESPESA) {
                throw new RegraDeNegocioException("error.transacao.categoria_incompativel_curta",
                        "Categoria incompatível com o tipo");
            }
        }

        var resultado = movimentacaoFinanceiraService.processarDespesaCartaoHistorico(
                dto.cartaoId(), candidata.data(), candidata.valor(), usuarioId);

        UsuarioEntity usuario = usuarioService.buscarPorIdOuFalhar(usuarioId);
        CartaoEntity cartao = resultado.cartao();

        TransacaoEntity t = new TransacaoEntity();
        t.setDescricao(candidata.descricao());
        t.setValor(candidata.valor());
        t.setData(candidata.data());
        t.setTipo(TipoTransacao.DESPESA);
        t.setStatus(StatusTransacao.PENDENTE);
        t.setMetodoPagamento(candidata.metodoPagamento() != null ? candidata.metodoPagamento() : MetodoPagamento.CARTAO_CREDITO);
        t.setCartao(cartao);
        t.setFatura(resultado.fatura());
        t.setCategoria(categoria);
        t.setUsuario(usuario);
        t.setNumeroParcela(candidata.numeroParcela());
        t.setTotalParcelas(candidata.totalParcelas());
        t.setIdempotencyKey(key);
        t.setOrigem(ORIGEM_IMPORTACAO);
        t.setImportacaoId(importacaoId);
        t.setTransferencia(false);
        t.setAtivo(true);
        transacaoRepository.save(t);
    }

    private String gerarIdempotencyKey(TransacaoCandidataDTO c, UUID importacaoId) {
        String raw = importacaoId + "|" + c.indice() + "|" + c.data() + "|" + c.valor() + "|" + c.descricao();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
