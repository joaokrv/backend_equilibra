package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.AjusteLinhaDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.VinculoInvestimentoDTO;
import org.app_financeiro.backend.dto.importacao.TransacaoCandidataDTO;
import org.app_financeiro.backend.dto.model.ResultadoMovimentacaoCartao;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.TransacaoResponseDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.ClassificacaoCandidata;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Roteamento por classificação (NORMAL/histórico/APORTE/RESGATE/TRANSFERENCIA_INTERNA),
 * determinismo da idempotency key e guards de "sem vínculo não vira despesa comum".
 */
@ExtendWith(MockitoExtension.class)
class ImportacaoItemProcessorTest {

    @Mock private TransacaoService transacaoService;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private MovimentacaoFinanceiraService movimentacaoFinanceiraService;
    @Mock private UsuarioService usuarioService;
    @Mock private InvestimentoService investimentoService;
    @Mock private CartaoService cartaoService;
    @Mock private CategoriaService categoriaService;

    private ImportacaoItemProcessor processor;

    private static final Long USUARIO_ID = 1L;
    private static final UUID IMPORTACAO_ID = UUID.randomUUID();

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        processor = new ImportacaoItemProcessor(transacaoService, transacaoRepository,
                movimentacaoFinanceiraService, usuarioService, investimentoService, cartaoService, categoriaService);
        // Fechamento dia 31 ⇒ mês de referência == mês civil, preservando os cenários dos testes.
        CartaoEntity cartao = new CartaoEntity();
        cartao.setDiaFechamento(31);
        lenient().when(cartaoService.buscarCartaoValidado(anyLong(), anyLong())).thenReturn(cartao);
    }

    private TransacaoCandidataDTO candidataClassificada(int indice, TipoTransacao tipo, LocalDate data,
                                                        ClassificacaoCandidata classificacao) {
        return new TransacaoCandidataDTO(indice, "Descricao " + indice, new BigDecimal("50.00"),
                tipo, data, false, null, null, null, false, false, classificacao, null);
    }

    private TransacaoCandidataDTO candidata(int indice, TipoTransacao tipo, LocalDate data) {
        return new TransacaoCandidataDTO(indice, "Descricao " + indice, new BigDecimal("50.00"),
                tipo, data, false, null, null, null, false, false);
    }

    @Test
    @DisplayName("DESPESA de cartão em mês passado é roteada para o caminho histórico (bypassa consumirLimite)")
    void deveRotearDespesaHistoricaParaCaminhoHistorico() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now().minusMonths(2));
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, null, 5L, null, List.of(0));

        when(transacaoRepository.existsByIdempotencyKey(any())).thenReturn(false);
        CartaoEntity cartao = new CartaoEntity();
        FaturaEntity fatura = new FaturaEntity();
        when(movimentacaoFinanceiraService.processarDespesaCartaoHistorico(eq(5L), eq(c.data()), eq(c.valor()), eq(USUARIO_ID)))
                .thenReturn(new ResultadoMovimentacaoCartao(cartao, fatura));
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(USUARIO_ID);
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(movimentacaoFinanceiraService).processarDespesaCartaoHistorico(5L, c.data(), c.valor(), USUARIO_ID);
        verify(transacaoService, never()).criarTransacao(any(), anyLong());
        verify(transacaoRepository).save(any(TransacaoEntity.class));
    }

    @Test
    @DisplayName("SEGURANÇA: RECEITA (estorno) em mês passado de cartão NUNCA é roteada como despesa histórica")
    void naoDeveRotearEstornoParaCaminhoHistorico() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.RECEITA, LocalDate.now().minusMonths(2));
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, null, 5L, null, List.of(0));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(99L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.CARTAO_CREDITO, null, null, null, null, null, 5L,
                        false, null, null, false));
        when(transacaoRepository.findById(99L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(movimentacaoFinanceiraService, never()).processarDespesaCartaoHistorico(any(), any(), any(), any());
        verify(transacaoService).criarTransacao(any(), eq(USUARIO_ID));
    }

    @Test
    @DisplayName("REGRESSÃO: compra do mês passado APÓS o fechamento pertence à fatura corrente — passa por consumirLimite, não pela rota histórica")
    void compraAposFechamentoDoMesPassadoNaoEhHistorica() {
        CartaoEntity cartaoFechamentoDia10 = new CartaoEntity();
        cartaoFechamentoDia10.setDiaFechamento(10);
        when(cartaoService.buscarCartaoValidado(5L, USUARIO_ID)).thenReturn(cartaoFechamentoDia10);

        // Dia 15 do mês passado, fechamento dia 10 ⇒ fatura de referência é a do mês ATUAL.
        LocalDate aposFechamentoMesPassado = LocalDate.now().minusMonths(1).withDayOfMonth(15);
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, aposFechamentoMesPassado);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, null, 5L, null, List.of(0));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(103L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.CARTAO_CREDITO, null, null, null, null, null, 5L,
                        false, null, null, false));
        when(transacaoRepository.findById(103L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(movimentacaoFinanceiraService, never()).processarDespesaCartaoHistorico(any(), any(), any(), any());
        verify(transacaoService).criarTransacao(any(), eq(USUARIO_ID));
    }

    @Test
    @DisplayName("DESPESA de cartão no mês corrente segue o fluxo normal (consumirLimite)")
    void deveRotearDespesaDoMesCorrenteParaFluxoNormal() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now());
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, null, 5L, null, List.of(0));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(100L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.CARTAO_CREDITO, null, null, null, null, null, 5L,
                        false, null, null, false));
        when(transacaoRepository.findById(100L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(movimentacaoFinanceiraService, never()).processarDespesaCartaoHistorico(any(), any(), any(), any());
        verify(transacaoService).criarTransacao(any(), eq(USUARIO_ID));
    }

    @Test
    @DisplayName("MÉTODO: destino cartão força CARTAO_CREDITO ignorando o método detectado no documento")
    void cartaoForcaMetodoCartaoCreditoIgnorandoDetectado() {
        TransacaoCandidataDTO c = new TransacaoCandidataDTO(0, "Compra", new BigDecimal("50.00"),
                TipoTransacao.DESPESA, LocalDate.now(), false, MetodoPagamento.BOLETO, null, null,
                false, false, ClassificacaoCandidata.NORMAL, null);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, null, 5L, null, List.of(0));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(110L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.CARTAO_CREDITO, null, null, null, null, null, 5L,
                        false, null, null, false));
        when(transacaoRepository.findById(110L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(transacaoService).criarTransacao(argThat(req ->
                req.metodoPagamento() == MetodoPagamento.CARTAO_CREDITO && req.status() == null), eq(USUARIO_ID));
    }

    @Test
    @DisplayName("MÉTODO: destino conta preserva o método detectado e força status PAGO (extrato = dinheiro já movimentado)")
    void contaPreservaMetodoEForcaStatusPago() {
        TransacaoCandidataDTO c = new TransacaoCandidataDTO(0, "Boleto Energia", new BigDecimal("50.00"),
                TipoTransacao.DESPESA, LocalDate.now(), false, MetodoPagamento.BOLETO, null, null,
                false, false, ClassificacaoCandidata.NORMAL, null);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, 7L, null, null, List.of(0));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(111L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        StatusTransacao.PAGO, MetodoPagamento.BOLETO, null, null, null, 7L, null, null,
                        false, null, null, false));
        when(transacaoRepository.findById(111L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(transacaoService).criarTransacao(argThat(req ->
                req.metodoPagamento() == MetodoPagamento.BOLETO && req.status() == StatusTransacao.PAGO), eq(USUARIO_ID));
    }

    @Test
    @DisplayName("AJUSTE: override de categoria do usuário é repassado ao criarTransacao (conta)")
    void ajusteDeCategoriaEhRepassadoParaContaConta() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now());
        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(0, null, 42L);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 7L, null, null, List.of(0), List.of(), List.of(), List.of(ajuste));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(112L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        StatusTransacao.PAGO, MetodoPagamento.PIX, null, 42L, null, 7L, null, null,
                        false, null, null, false));
        when(transacaoRepository.findById(112L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(transacaoService).criarTransacao(argThat(req -> req.categoriaId().equals(42L)), eq(USUARIO_ID));
    }

    @Test
    @DisplayName("AJUSTE: override de método do usuário tem prioridade sobre o método detectado no documento (conta)")
    void ajusteDeMetodoSobrepoeMetodoDetectado() {
        TransacaoCandidataDTO c = new TransacaoCandidataDTO(0, "Compra", new BigDecimal("50.00"),
                TipoTransacao.DESPESA, LocalDate.now(), false, MetodoPagamento.BOLETO, null, null,
                false, false, ClassificacaoCandidata.NORMAL, null);
        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(0, MetodoPagamento.DINHEIRO, null);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 7L, null, null, List.of(0), List.of(), List.of(), List.of(ajuste));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(113L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        StatusTransacao.PAGO, MetodoPagamento.DINHEIRO, null, null, null, 7L, null, null,
                        false, null, null, false));
        when(transacaoRepository.findById(113L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(transacaoService).criarTransacao(argThat(req ->
                req.metodoPagamento() == MetodoPagamento.DINHEIRO), eq(USUARIO_ID));
    }

    @Test
    @DisplayName("AJUSTE: categoria é ignorada em cartão (destino cartão bypassa TransacaoService no histórico) mas aplicada no fluxo normal de cartão")
    void ajusteDeCategoriaAplicadoNoCartaoFluxoNormal() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now());
        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(0, null, 9L);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, null, 5L, null, List.of(0), List.of(), List.of(), List.of(ajuste));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(114L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.CARTAO_CREDITO, null, 9L, null, null, null, 5L,
                        false, null, null, false));
        when(transacaoRepository.findById(114L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(transacaoService).criarTransacao(argThat(req ->
                req.categoriaId().equals(9L) && req.metodoPagamento() == MetodoPagamento.CARTAO_CREDITO), eq(USUARIO_ID));
    }

    @Test
    @DisplayName("AJUSTE: categoria aplicada no caminho de cartão histórico, validada por CategoriaService")
    void ajusteDeCategoriaAplicadoEValidadoNoCartaoHistorico() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now().minusMonths(2));
        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(0, null, 9L);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, null, 5L, null, List.of(0), List.of(), List.of(), List.of(ajuste));

        when(transacaoRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(movimentacaoFinanceiraService.processarDespesaCartaoHistorico(eq(5L), eq(c.data()), eq(c.valor()), eq(USUARIO_ID)))
                .thenReturn(new ResultadoMovimentacaoCartao(new CartaoEntity(), new FaturaEntity()));
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(USUARIO_ID);
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setTipo(TipoTransacao.DESPESA);
        when(categoriaService.buscarPorIdOuFalhar(9L, USUARIO_ID)).thenReturn(categoria);

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        ArgumentCaptor<TransacaoEntity> captor = ArgumentCaptor.forClass(TransacaoEntity.class);
        verify(transacaoRepository).save(captor.capture());
        assertThat(captor.getValue().getCategoria()).isSameAs(categoria);
    }

    @Test
    @DisplayName("SEGURANÇA: categoria de outro usuário no cartão histórico é rejeitada antes de salvar (IDOR)")
    void ajusteDeCategoriaIdorNoCartaoHistoricoEhRejeitado() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now().minusMonths(2));
        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(0, null, 9L);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, null, 5L, null, List.of(0), List.of(), List.of(), List.of(ajuste));

        when(transacaoRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(categoriaService.buscarPorIdOuFalhar(9L, USUARIO_ID))
                .thenThrow(new RecursoNaoEncontradoException("Categoria não pertence ao usuário"));

        assertThatThrownBy(() -> processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(transacaoRepository, never()).save(any());
        // Categoria é validada ANTES de mutar fatura/limite — rejeição não pode gastar efeito financeiro.
        verifyNoInteractions(movimentacaoFinanceiraService);
    }

    @Test
    @DisplayName("Categoria de tipo incompatível (RECEITA) no cartão histórico (sempre DESPESA) é rejeitada")
    void ajusteDeCategoriaTipoIncompativelNoCartaoHistoricoEhRejeitado() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now().minusMonths(2));
        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(0, null, 9L);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, null, 5L, null, List.of(0), List.of(), List.of(), List.of(ajuste));

        when(transacaoRepository.existsByIdempotencyKey(any())).thenReturn(false);
        CategoriaEntity categoriaReceita = new CategoriaEntity();
        categoriaReceita.setTipo(TipoTransacao.RECEITA);
        when(categoriaService.buscarPorIdOuFalhar(9L, USUARIO_ID)).thenReturn(categoriaReceita);

        assertThatThrownBy(() -> processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(transacaoRepository, never()).save(any());
        verifyNoInteractions(movimentacaoFinanceiraService);
    }

    @Test
    @DisplayName("AJUSTE: override enviado para APORTE é ignorado — investimento nunca recebe categoria/método do documento")
    void ajusteEhIgnoradoParaAporte() {
        TransacaoCandidataDTO c = candidataClassificada(0, TipoTransacao.DESPESA, LocalDate.now(), ClassificacaoCandidata.APORTE);
        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(0, MetodoPagamento.PIX, 9L);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 7L, null, null, List.of(0),
                List.of(new VinculoInvestimentoDTO(0, 3L, true)), List.of(), List.of(ajuste));

        when(investimentoService.aportarImportado(eq(3L), eq(c.valor()), eq(7L), eq(c.data()), any(), eq(true), eq(USUARIO_ID)))
                .thenReturn(200L);
        when(transacaoRepository.findById(200L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verifyNoInteractions(categoriaService);
        verify(transacaoService, never()).criarTransacao(any(), any());
    }

    @Test
    @DisplayName("Transação sem cartão (conta) nunca é roteada para o caminho histórico, mesmo com data antiga")
    void deveRotearDespesaDeContaSempreParaFluxoNormal() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now().minusMonths(6));
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, 7L, null, null, List.of(0));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(101L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.PIX, null, null, null, 7L, null, null,
                        false, null, null, false));
        when(transacaoRepository.findById(101L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verifyNoInteractions(movimentacaoFinanceiraService);
    }

    @Test
    @DisplayName("Parcelas do documento são apenas informativas — nunca repassadas ao criarTransacao (evita explosão em N parcelas)")
    void naoDevePropagarParcelasParaCriarTransacao() {
        TransacaoCandidataDTO c = new TransacaoCandidataDTO(0, "Compra parcelada", new BigDecimal("120.00"),
                TipoTransacao.DESPESA, LocalDate.now(), false, null, 3, 12, false, false);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, null, 5L, null, List.of(0));

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(102L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.CARTAO_CREDITO, null, null, null, null, null, 5L,
                        false, null, null, false));
        TransacaoEntity entidade = new TransacaoEntity();
        when(transacaoRepository.findById(102L)).thenReturn(Optional.of(entidade));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(transacaoService).criarTransacao(argThat(req ->
                req.totalParcelas() == null && req.numeroParcela() == null), eq(USUARIO_ID));
        assertThat(entidade.getNumeroParcela()).isEqualTo(3);
        assertThat(entidade.getTotalParcelas()).isEqualTo(12);
    }

    @Test
    @DisplayName("Idempotency key é determinística: mesma candidata + mesma importação produz a mesma chave")
    void idempotencyKeyDeveSerDeterministica() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now().minusMonths(2));
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, null, 5L, null, List.of(0));

        when(transacaoRepository.existsByIdempotencyKey(any())).thenReturn(false);
        when(movimentacaoFinanceiraService.processarDespesaCartaoHistorico(any(), any(), any(), any()))
                .thenReturn(new ResultadoMovimentacaoCartao(new CartaoEntity(), new FaturaEntity()));
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(USUARIO_ID);
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario);

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);
        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(transacaoRepository, times(2)).existsByIdempotencyKey(captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
    }

    @Test
    @DisplayName("ANTI-ABUSO: chave de idempotência já existente no caminho histórico evita recriar a transação (retry seguro)")
    void devePularCriacaoQuandoIdempotencyKeyJaExiste() {
        TransacaoCandidataDTO c = candidata(0, TipoTransacao.DESPESA, LocalDate.now().minusMonths(2));
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(IMPORTACAO_ID, null, 5L, null, List.of(0));

        when(transacaoRepository.existsByIdempotencyKey(any())).thenReturn(true);

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verifyNoInteractions(movimentacaoFinanceiraService);
        verify(transacaoRepository, never()).save(any());
    }

    // ─── Roteamento de investimento (Fase 2) ──────────────────────────────────

    @Test
    @DisplayName("APORTE com vínculo roteia para InvestimentoService.aportarImportado — nunca vira despesa comum")
    void deveRotearAporteComVinculoParaInvestimentoService() {
        TransacaoCandidataDTO c = candidataClassificada(0, TipoTransacao.DESPESA, LocalDate.now(),
                ClassificacaoCandidata.APORTE);
        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(0, 77L, true);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 5L, null, null, List.of(0), List.of(vinculo), List.of());
        when(investimentoService.aportarImportado(eq(77L), eq(c.valor()), eq(5L), eq(c.data()),
                any(), eq(true), eq(USUARIO_ID))).thenReturn(300L);
        TransacaoEntity transacaoCriada = new TransacaoEntity();
        when(transacaoRepository.findById(300L)).thenReturn(Optional.of(transacaoCriada));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(investimentoService).aportarImportado(eq(77L), eq(c.valor()), eq(5L), eq(c.data()),
                any(), eq(true), eq(USUARIO_ID));
        verifyNoInteractions(transacaoService);
        // Regressão: sem isto marcado, desfazerImportacao nunca encontraria esta transação para reverter.
        assertThat(transacaoCriada.getOrigem()).isEqualTo("IMPORTACAO");
        assertThat(transacaoCriada.getImportacaoId()).isEqualTo(IMPORTACAO_ID);
        verify(transacaoRepository).save(transacaoCriada);
    }

    @Test
    @DisplayName("RESGATE com vínculo e atualizarValor=false roteia preservando a flag")
    void deveRotearResgateComVinculoSemAtualizarValor() {
        TransacaoCandidataDTO c = candidataClassificada(0, TipoTransacao.RECEITA, LocalDate.now(),
                ClassificacaoCandidata.RESGATE);
        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(0, 88L, false);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 5L, null, null, List.of(0), List.of(vinculo), List.of());
        when(investimentoService.resgatarImportado(eq(88L), eq(c.valor()), eq(5L), eq(c.data()),
                any(), eq(false), eq(USUARIO_ID))).thenReturn(301L);
        TransacaoEntity transacaoCriada = new TransacaoEntity();
        when(transacaoRepository.findById(301L)).thenReturn(Optional.of(transacaoCriada));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(investimentoService).resgatarImportado(eq(88L), eq(c.valor()), eq(5L), eq(c.data()),
                any(), eq(false), eq(USUARIO_ID));
        assertThat(transacaoCriada.getOrigem()).isEqualTo("IMPORTACAO");
        assertThat(transacaoCriada.getImportacaoId()).isEqualTo(IMPORTACAO_ID);
    }

    @Test
    @DisplayName("APORTE sem vínculo lança erro de item — nunca é criada como despesa comum silenciosamente")
    void deveLancarErroQuandoAporteSemVinculo() {
        TransacaoCandidataDTO c = candidataClassificada(0, TipoTransacao.DESPESA, LocalDate.now(),
                ClassificacaoCandidata.APORTE);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 5L, null, null, List.of(0));

        assertThatThrownBy(() -> processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
        verifyNoInteractions(investimentoService, transacaoService, movimentacaoFinanceiraService);
    }

    @Test
    @DisplayName("RESGATE sem vínculo lança erro de item")
    void deveLancarErroQuandoResgateSemVinculo() {
        TransacaoCandidataDTO c = candidataClassificada(0, TipoTransacao.RECEITA, LocalDate.now(),
                ClassificacaoCandidata.RESGATE);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 5L, null, null, List.of(0));

        assertThatThrownBy(() -> processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("APORTE com vínculo cujo índice não bate com a candidata não é encontrado — erro de item")
    void deveLancarErroQuandoVinculoNaoCorrespondeAoIndice() {
        TransacaoCandidataDTO c = candidataClassificada(1, TipoTransacao.DESPESA, LocalDate.now(),
                ClassificacaoCandidata.APORTE);
        VinculoInvestimentoDTO vinculoDeOutroIndice = new VinculoInvestimentoDTO(0, 77L, true);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 5L, null, null, List.of(0, 1), List.of(vinculoDeOutroIndice), List.of());

        assertThatThrownBy(() -> processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("Candidata TRANSFERENCIA_INTERNA marcada em indicesTransferencia roteia como transferência interna (isTransferencia=true)")
    void deveRotearTransferenciaInternaQuandoMarcada() {
        TransacaoCandidataDTO c = candidataClassificada(0, TipoTransacao.RECEITA, LocalDate.now(),
                ClassificacaoCandidata.TRANSFERENCIA_INTERNA);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 5L, null, null, List.of(0), List.of(), List.of(0));

        when(transacaoService.criarTransacaoInterna(any(), eq(USUARIO_ID), eq(true)))
                .thenReturn(new TransacaoResponseDTO(200L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.TRANSFERENCIA, null, null, null, 5L, null, null,
                        false, null, null, true));
        when(transacaoRepository.findById(200L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(transacaoService).criarTransacaoInterna(any(), eq(USUARIO_ID), eq(true));
        verifyNoInteractions(investimentoService);
    }

    @Test
    @DisplayName("Candidata classificada TRANSFERENCIA_INTERNA mas NÃO marcada em indicesTransferencia segue o fluxo normal (receita comum)")
    void naoDeveRotearTransferenciaSemMarcacaoExplicita() {
        TransacaoCandidataDTO c = candidataClassificada(0, TipoTransacao.RECEITA, LocalDate.now(),
                ClassificacaoCandidata.TRANSFERENCIA_INTERNA);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 5L, null, null, List.of(0)); // indicesTransferencia vazio (default)

        when(transacaoService.criarTransacao(any(), eq(USUARIO_ID)))
                .thenReturn(new TransacaoResponseDTO(201L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.PIX, null, null, null, 5L, null, null,
                        false, null, null, false));
        when(transacaoRepository.findById(201L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(transacaoService).criarTransacao(any(), eq(USUARIO_ID));
        verify(transacaoService, never()).criarTransacaoInterna(any(), any(), anyBoolean());
        verifyNoInteractions(investimentoService);
    }

    @Test
    @DisplayName("NORMAL selecionado como transferência (indicesTransferencia) também roteia como transferência — usuário pode sobrepor a IA")
    void deveRotearComoTransferenciaMesmoQuandoClassificacaoEhNormal() {
        TransacaoCandidataDTO c = candidataClassificada(0, TipoTransacao.RECEITA, LocalDate.now(),
                ClassificacaoCandidata.NORMAL);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                IMPORTACAO_ID, 5L, null, null, List.of(0), List.of(), List.of(0));

        when(transacaoService.criarTransacaoInterna(any(), eq(USUARIO_ID), eq(true)))
                .thenReturn(new TransacaoResponseDTO(202L, c.descricao(), c.valor(), c.data(), c.tipo(),
                        null, MetodoPagamento.TRANSFERENCIA, null, null, null, 5L, null, null,
                        false, null, null, true));
        when(transacaoRepository.findById(202L)).thenReturn(Optional.of(new TransacaoEntity()));

        processor.processar(c, dto, USUARIO_ID, IMPORTACAO_ID);

        verify(transacaoService).criarTransacaoInterna(any(), eq(USUARIO_ID), eq(true));
    }
}
