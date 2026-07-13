package org.app_financeiro.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO;
import org.app_financeiro.backend.dto.importacao.ImportacaoIniciadaDTO;
import org.app_financeiro.backend.dto.importacao.TransacaoCandidataDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.AjusteLinhaDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.VinculoInvestimentoDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.ImportacaoEntity;
import org.app_financeiro.backend.entity.MovimentacaoInvestimentoEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.FormatoDetectado;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusImportacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.enums.ClassificacaoCandidata;
import org.app_financeiro.backend.repository.ImportacaoRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.MovimentacaoInvestimentoRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Cobre o saneamento anti-abuso (defesa contra prompt injection / CSV malicioso) e o claim
 * atômico de confirmação/cancelamento/desfazer introduzido na Fase 0 de concorrência.
 */
@ExtendWith(MockitoExtension.class)
class ImportacaoServiceTest {

    @Mock private DocumentoDetector documentoDetector;
    @Mock private CsvExtratoParser csvExtratoParser;
    @Mock private GeminiClient geminiClient;
    @Mock private ImportacaoRepository importacaoRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private InvestimentoRepository investimentoRepository;
    @Mock private MovimentacaoInvestimentoRepository movimentacaoInvestimentoRepository;
    @Mock private ImportacaoItemProcessor itemProcessor;
    @Mock private FaturaService faturaService;
    @Mock private CartaoService cartaoService;
    @Mock private UsuarioService usuarioService;
    @Mock private MovimentacaoFinanceiraService movimentacaoFinanceiraService;
    @Mock private InvestimentoService investimentoService;
    @Mock private PatrimonioHistoricoService patrimonioHistoricoService;

    private ImportacaoService service;

    private static final Long USUARIO_ID = 1L;
    private static final Long OUTRO_USUARIO_ID = 2L;

    @BeforeEach
    void setUp() {
        // ObjectMapper real: comportamento de serialização/deserialização das candidatas
        // é parte do contrato testado (JSONB da sessão), não deve ser mockado.
        service = new ImportacaoService(documentoDetector, csvExtratoParser, geminiClient,
                importacaoRepository, transacaoRepository, investimentoRepository,
                movimentacaoInvestimentoRepository, itemProcessor,
                faturaService, cartaoService, usuarioService, movimentacaoFinanceiraService,
                investimentoService, patrimonioHistoricoService,
                new ObjectMapper().findAndRegisterModules());
    }

    private UsuarioEntity usuario(Long id) {
        UsuarioEntity u = new UsuarioEntity();
        u.setId(id);
        u.setNome("Fulano De Tal Teste");
        return u;
    }

    private TransacaoCandidataDTO candidata(int indice, BigDecimal valor, LocalDate data, String descricao) {
        return new TransacaoCandidataDTO(indice, descricao, valor, TipoTransacao.DESPESA, data,
                false, MetodoPagamento.PIX, null, null, false, false);
    }

    // ─── sanearCandidatas (via iniciarImportacao) — defesa anti-abuso ─────────

    @Test
    @DisplayName("Saneamento descarta candidatas com valor <= 0, valor acima do teto, data fora dos limites e campos nulos")
    void deveDescartarCandidatasInvalidasNoSaneamento() throws Exception {
        LocalDate hoje = LocalDate.now();
        List<TransacaoCandidataDTO> brutas = List.of(
                candidata(0, new BigDecimal("100.00"), hoje, "Válida"),
                candidata(1, new BigDecimal("-10.00"), hoje, "Valor negativo"),
                candidata(2, BigDecimal.ZERO, hoje, "Valor zero"),
                candidata(3, new BigDecimal("10000000.00"), hoje, "Acima do teto"),
                candidata(4, new BigDecimal("50.00"), LocalDate.of(1990, 1, 1), "Data muito antiga"),
                candidata(5, new BigDecimal("50.00"), hoje.plusYears(2), "Data no futuro distante"),
                candidata(6, new BigDecimal("50.00"), hoje, ""),
                new TransacaoCandidataDTO(7, "Sem tipo", new BigDecimal("50.00"), null, hoje,
                        false, MetodoPagamento.PIX, null, null, false, false)
        );

        ImportacaoIniciadaDTO resultado = executarIniciarImportacao(brutas);

        assertThat(resultado.candidatas()).hasSize(1);
        assertThat(resultado.candidatas().get(0).descricao()).isEqualTo("Válida");
    }

    @Test
    @DisplayName("Saneamento trunca descrição acima de 255 caracteres")
    void deveTruncarDescricaoLonga() throws Exception {
        String descricaoGigante = "A".repeat(500);
        List<TransacaoCandidataDTO> brutas = List.of(
                candidata(0, new BigDecimal("10.00"), LocalDate.now(), descricaoGigante));

        ImportacaoIniciadaDTO resultado = executarIniciarImportacao(brutas);

        assertThat(resultado.candidatas().get(0).descricao()).hasSize(255);
    }

    @Test
    @DisplayName("Saneamento invalida parcelas fora dos limites (totalParcelas > 72, numeroParcela > totalParcelas)")
    void deveInvalidarParcelasForaDosLimites() throws Exception {
        List<TransacaoCandidataDTO> brutas = List.of(
                new TransacaoCandidataDTO(0, "Compra parcelada além do limite", new BigDecimal("10.00"),
                        TipoTransacao.DESPESA, LocalDate.now(), false, MetodoPagamento.CARTAO_CREDITO,
                        1, 100, false, false),
                new TransacaoCandidataDTO(1, "Parcela maior que o total", new BigDecimal("10.00"),
                        TipoTransacao.DESPESA, LocalDate.now(), false, MetodoPagamento.CARTAO_CREDITO,
                        5, 3, false, false));

        ImportacaoIniciadaDTO resultado = executarIniciarImportacao(brutas);

        assertThat(resultado.candidatas()).hasSize(2);
        assertThat(resultado.candidatas()).allSatisfy(c -> {
            assertThat(c.numeroParcela()).isNull();
            assertThat(c.totalParcelas()).isNull();
        });
    }

    @Test
    @DisplayName("Documento com mais de 500 candidatas é rejeitado antes de qualquer persistência")
    void deveRejeitarLoteAcimaDoLimite() {
        List<TransacaoCandidataDTO> brutas = java.util.stream.IntStream.range(0, 501)
                .mapToObj(i -> candidata(i, new BigDecimal("10.00"), LocalDate.now(), "Item " + i))
                .toList();

        when(documentoDetector.detectar(any())).thenReturn(FormatoDetectado.CSV);
        when(csvExtratoParser.tentarParsear(any())).thenReturn(Optional.of(brutas));

        assertThatThrownBy(() -> service.iniciarImportacao(arquivoFake(), USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);

        verify(importacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Documento sem nenhuma candidata válida após saneamento é rejeitado")
    void deveRejeitarQuandoTodasAsCandidatasSaoInvalidas() {
        List<TransacaoCandidataDTO> brutas = List.of(
                candidata(0, BigDecimal.ZERO, LocalDate.now(), "Inválida"));

        when(documentoDetector.detectar(any())).thenReturn(FormatoDetectado.CSV);
        when(csvExtratoParser.tentarParsear(any())).thenReturn(Optional.of(brutas));

        assertThatThrownBy(() -> service.iniciarImportacao(arquivoFake(), USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    private ImportacaoIniciadaDTO executarIniciarImportacao(List<TransacaoCandidataDTO> brutas) throws Exception {
        when(documentoDetector.detectar(any())).thenReturn(FormatoDetectado.CSV);
        when(csvExtratoParser.tentarParsear(any())).thenReturn(Optional.of(brutas));
        when(transacaoRepository.buscarIndicesDuplicados(anyLong(), any())).thenReturn(List.of());
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario(USUARIO_ID));

        return service.iniciarImportacao(arquivoFake(), USUARIO_ID);
    }

    private MockMultipartFile arquivoFake() {
        return new MockMultipartFile("arquivo", "extrato.csv", "text/csv", new byte[]{1});
    }

    @Test
    @DisplayName("CSV de estrutura não reconhecida cai no fallback via IA (text/plain) e segue o pipeline normal")
    void deveUsarFallbackDeIaParaCsvNaoReconhecido() {
        when(documentoDetector.detectar(any())).thenReturn(FormatoDetectado.CSV);
        when(csvExtratoParser.tentarParsear(any())).thenReturn(Optional.empty());
        when(geminiClient.extrairTexto(any(), eq("text/plain"), any(), eq(USUARIO_ID))).thenReturn("""
                {"transacoes":[{"indice":0,"descricao":"Compra Via Fallback","valor":10.00,
                  "tipo":"DESPESA","data":"2026-03-01","dataPresumida":false,"suspeita":false}]}
                """);
        when(transacaoRepository.buscarIndicesDuplicados(anyLong(), any())).thenReturn(List.of());
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuario(USUARIO_ID));

        ImportacaoIniciadaDTO resultado = service.iniciarImportacao(arquivoFake(), USUARIO_ID);

        assertThat(resultado.candidatas()).hasSize(1);
        assertThat(resultado.candidatas().get(0).descricao()).isEqualTo("Compra Via Fallback");
        verify(geminiClient).extrairTexto(any(), eq("text/plain"), any(), eq(USUARIO_ID));
    }

    // ─── enriquecerClassificacao (Fase 1) — heurísticas apenas sugerem ────────

    private TransacaoCandidataDTO candidataClassificada(int indice, String descricao,
                                                        ClassificacaoCandidata classificacao) {
        return new TransacaoCandidataDTO(indice, descricao, new BigDecimal("100.00"),
                classificacao == ClassificacaoCandidata.RESGATE ? TipoTransacao.RECEITA : TipoTransacao.DESPESA,
                LocalDate.now(), false, MetodoPagamento.PIX, null, null,
                classificacao != ClassificacaoCandidata.NORMAL, false, classificacao, null);
    }

    private InvestimentoEntity investimento(Long id, String descricao) {
        InvestimentoEntity inv = new InvestimentoEntity();
        inv.setId(id);
        inv.setDescricao(descricao);
        return inv;
    }

    @Test
    @DisplayName("Pix contendo o nome completo do usuário → sugestão TRANSFERENCIA_INTERNA com suspeita=true")
    void deveSugerirTransferenciaInternaParaPixComNomeProprio() throws Exception {
        List<TransacaoCandidataDTO> brutas = List.of(
                candidataClassificada(0, "Pix Recebido Fulano De Tal Teste", ClassificacaoCandidata.NORMAL));

        ImportacaoIniciadaDTO resultado = executarIniciarImportacao(brutas);

        TransacaoCandidataDTO c = resultado.candidatas().get(0);
        assertThat(c.classificacao()).isEqualTo(ClassificacaoCandidata.TRANSFERENCIA_INTERNA);
        assertThat(c.suspeita()).isTrue();
    }

    @Test
    @DisplayName("Pix com nome de terceiro permanece NORMAL — heurística não dispara para homônimos parciais")
    void naoDeveSugerirTransferenciaParaNomeDeTerceiro() throws Exception {
        List<TransacaoCandidataDTO> brutas = List.of(
                candidataClassificada(0, "Pix Recebido Beltrano Da Silva", ClassificacaoCandidata.NORMAL));

        ImportacaoIniciadaDTO resultado = executarIniciarImportacao(brutas);

        assertThat(resultado.candidatas().get(0).classificacao()).isEqualTo(ClassificacaoCandidata.NORMAL);
    }

    @Test
    @DisplayName("Transação com nome próprio mas sem 'Pix' na descrição permanece NORMAL")
    void naoDeveSugerirTransferenciaSemPixNaDescricao() throws Exception {
        List<TransacaoCandidataDTO> brutas = List.of(
                candidataClassificada(0, "TED Fulano De Tal Teste", ClassificacaoCandidata.NORMAL));

        ImportacaoIniciadaDTO resultado = executarIniciarImportacao(brutas);

        assertThat(resultado.candidatas().get(0).classificacao()).isEqualTo(ClassificacaoCandidata.NORMAL);
    }

    @Test
    @DisplayName("Usuário com nome de uma só palavra desativa a heurística — evitaria falso positivo em massa")
    void naoDeveSugerirTransferenciaQuandoNomeTemUmaSoPalavra() throws Exception {
        UsuarioEntity usuarioNomeCurto = new UsuarioEntity();
        usuarioNomeCurto.setId(USUARIO_ID);
        usuarioNomeCurto.setNome("Fulano");

        when(documentoDetector.detectar(any())).thenReturn(FormatoDetectado.CSV);
        when(csvExtratoParser.tentarParsear(any())).thenReturn(Optional.of(List.of(
                candidataClassificada(0, "Pix Recebido Fulano", ClassificacaoCandidata.NORMAL))));
        when(transacaoRepository.buscarIndicesDuplicados(anyLong(), any())).thenReturn(List.of());
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuarioNomeCurto);

        ImportacaoIniciadaDTO resultado = service.iniciarImportacao(arquivoFake(), USUARIO_ID);

        assertThat(resultado.candidatas().get(0).classificacao()).isEqualTo(ClassificacaoCandidata.NORMAL);
    }

    @Test
    @DisplayName("Heurística de nome é insensível a acentos e caixa (Aplicação ≈ aplicacao)")
    void heuristicaDeNomeIgnoraAcentosECaixa() throws Exception {
        UsuarioEntity usuarioAcentuado = new UsuarioEntity();
        usuarioAcentuado.setId(USUARIO_ID);
        usuarioAcentuado.setNome("João Conceição");

        when(documentoDetector.detectar(any())).thenReturn(FormatoDetectado.CSV);
        when(csvExtratoParser.tentarParsear(any())).thenReturn(Optional.of(List.of(
                candidataClassificada(0, "PIX RECEBIDO JOAO CONCEICAO", ClassificacaoCandidata.NORMAL))));
        when(transacaoRepository.buscarIndicesDuplicados(anyLong(), any())).thenReturn(List.of());
        when(usuarioService.buscarPorIdOuFalhar(USUARIO_ID)).thenReturn(usuarioAcentuado);

        ImportacaoIniciadaDTO resultado = service.iniciarImportacao(arquivoFake(), USUARIO_ID);

        assertThat(resultado.candidatas().get(0).classificacao())
                .isEqualTo(ClassificacaoCandidata.TRANSFERENCIA_INTERNA);
    }

    @Test
    @DisplayName("APORTE com investimento de nome compatível recebe investimentoSugeridoId")
    void deveSugerirInvestimentoParaAportePorNome() throws Exception {
        when(investimentoRepository.findByUsuarioId(USUARIO_ID)).thenReturn(List.of(
                investimento(30L, "Tesouro Selic"),
                investimento(31L, "CDB Banco Inter")));
        List<TransacaoCandidataDTO> brutas = List.of(
                candidataClassificada(0, "Aplicação Cdb Porq Obj Banco Inter S A", ClassificacaoCandidata.APORTE));

        ImportacaoIniciadaDTO resultado = executarIniciarImportacao(brutas);

        assertThat(resultado.candidatas().get(0).investimentoSugeridoId()).isEqualTo(31L);
        assertThat(resultado.candidatas().get(0).classificacao()).isEqualTo(ClassificacaoCandidata.APORTE);
    }

    @Test
    @DisplayName("RESGATE sem investimento compatível fica sem sugestão (usuário escolhe manualmente)")
    void naoDeveSugerirInvestimentoSemMatch() throws Exception {
        when(investimentoRepository.findByUsuarioId(USUARIO_ID)).thenReturn(List.of(
                investimento(30L, "Tesouro Selic")));
        List<TransacaoCandidataDTO> brutas = List.of(
                candidataClassificada(0, "Resgate Poupanca Caixa", ClassificacaoCandidata.RESGATE));

        ImportacaoIniciadaDTO resultado = executarIniciarImportacao(brutas);

        assertThat(resultado.candidatas().get(0).investimentoSugeridoId()).isNull();
    }

    @Test
    @DisplayName("Lote sem APORTE/RESGATE não consulta investimentos (lazy)")
    void naoDeveConsultarInvestimentosSemCandidatasInvestiveis() throws Exception {
        List<TransacaoCandidataDTO> brutas = List.of(
                candidataClassificada(0, "Compra Comum", ClassificacaoCandidata.NORMAL));

        executarIniciarImportacao(brutas);

        verifyNoInteractions(investimentoRepository);
    }

    @Test
    @DisplayName("SEGURANÇA: investimentoSugeridoId vindo da fonte externa é zerado no saneamento — só a heurística do servidor sugere")
    void saneamentoZeraSugestaoVindaDaFonteExterna() throws Exception {
        // Simula prompt injection: PDF instruiu a IA a devolver um investimentoSugeridoId forjado.
        TransacaoCandidataDTO forjada = new TransacaoCandidataDTO(0, "Compra Comum Sem Match",
                new BigDecimal("10.00"), TipoTransacao.DESPESA, LocalDate.now(), false,
                MetodoPagamento.PIX, null, null, false, false,
                ClassificacaoCandidata.NORMAL, 999L);

        ImportacaoIniciadaDTO resultado = executarIniciarImportacao(List.of(forjada));

        assertThat(resultado.candidatas().get(0).investimentoSugeridoId()).isNull();
    }

    @Test
    @DisplayName("Sessão antiga (JSONB sem classificacao) deserializa como NORMAL sem quebrar")
    void sessaoAntigaSemClassificacaoViraNormal() throws Exception {
        String jsonAntigo = """
                [{"indice":0,"descricao":"Compra Legada","valor":10.00,"tipo":"DESPESA",
                  "data":"2026-03-01","dataPresumida":false,"metodoPagamento":"PIX",
                  "numeroParcela":null,"totalParcelas":null,"suspeita":false,"duplicataDetectada":false}]
                """;
        List<TransacaoCandidataDTO> candidatas = new ObjectMapper().findAndRegisterModules()
                .readValue(jsonAntigo, new com.fasterxml.jackson.core.type.TypeReference<List<TransacaoCandidataDTO>>() {});

        assertThat(candidatas.get(0).classificacao()).isEqualTo(ClassificacaoCandidata.NORMAL);
        assertThat(candidatas.get(0).investimentoSugeridoId()).isNull();
    }

    // ─── confirmarImportacao — claim atômico (Fase 0.A) ───────────────────────

    private ImportacaoEntity sessaoPendente(UUID id, Long donoId, String candidatasJson) {
        ImportacaoEntity sessao = new ImportacaoEntity();
        sessao.setId(id);
        sessao.setUsuario(usuario(donoId));
        sessao.setStatus(StatusImportacao.PENDENTE);
        sessao.setCandidatas(candidatasJson);
        return sessao;
    }

    private String candidatasJson(TransacaoCandidataDTO... candidatas) throws Exception {
        return new ObjectMapper().findAndRegisterModules().writeValueAsString(List.of(candidatas));
    }

    @Test
    @DisplayName("Confirmar sessão inexistente → RecursoNaoEncontradoException (404)")
    void deveFalharAoConfirmarSessaoInexistente() {
        UUID id = UUID.randomUUID();
        when(importacaoRepository.findById(id)).thenReturn(Optional.empty());

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(id, 10L, null, null, List.of(0));

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("SEGURANÇA (IDOR): usuário B não confirma sessão pertencente ao usuário A")
    void deveFalharAoConfirmarSessaoDeOutroUsuario() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessaoDeOutro = sessaoPendente(id, OUTRO_USUARIO_ID, candidatasJson());
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessaoDeOutro));

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(id, 10L, null, null, List.of(0));

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(importacaoRepository, never()).reivindicarParaConfirmacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Confirmar sessão já CONFIRMADA → erro sem tentar reivindicar novamente")
    void deveFalharAoConfirmarSessaoJaConfirmada() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        sessao.setStatus(StatusImportacao.CONFIRMADA);
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(id, 10L, null, null, List.of(0));

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("já foi confirmada");
        verify(importacaoRepository, never()).reivindicarParaConfirmacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("anoPresumido no futuro é rejeitado antes do claim (0.C)")
    void deveRejeitarAnoPresumidoNoFuturo() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));

        int anoFuturo = LocalDate.now().getYear() + 1;
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(id, 10L, null, anoFuturo, List.of(0));

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("futuro");
        verify(importacaoRepository, never()).reivindicarParaConfirmacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CONCORRÊNCIA: claim retornando 0 (já reivindicada por outra requisição) impede o processamento do lote")
    void deveFalharQuandoClaimNaoReivindicaNinguem() throws Exception {
        UUID id = UUID.randomUUID();
        TransacaoCandidataDTO c0 = candidata(0, new BigDecimal("10.00"), LocalDate.now(), "Item 0");
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(c0));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));
        when(importacaoRepository.reivindicarParaConfirmacao(eq(id), any(), any(), any(), any())).thenReturn(0);

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(id, 10L, null, null, List.of(0));

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("processada");
        verifyNoInteractions(itemProcessor);
        verify(importacaoRepository, never()).finalizarConfirmacao(any(), any(), any());
    }

    @Test
    @DisplayName("Claim bem-sucedido processa cada candidata selecionada e finaliza a sessão")
    void deveProcessarLoteAposClaimBemSucedido() throws Exception {
        UUID id = UUID.randomUUID();
        TransacaoCandidataDTO c0 = candidata(0, new BigDecimal("10.00"), LocalDate.now(), "Item 0");
        TransacaoCandidataDTO c1 = candidata(1, new BigDecimal("20.00"), LocalDate.now(), "Item 1");
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(c0, c1));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));
        when(importacaoRepository.reivindicarParaConfirmacao(eq(id), any(), any(), any(), any())).thenReturn(1);

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(id, 10L, null, null, List.of(0, 1));

        var resultado = service.confirmarImportacao(dto, USUARIO_ID);

        assertThat(resultado.criadas()).isEqualTo(2);
        assertThat(resultado.erros()).isZero();
        verify(itemProcessor, times(2)).processar(any(), eq(dto), eq(USUARIO_ID), eq(id));
        verify(importacaoRepository).finalizarConfirmacao(id, StatusImportacao.CONFIRMADA, StatusImportacao.PROCESSANDO);
    }

    @Test
    @DisplayName("Falha em uma candidata não aborta o lote — resultado parcial fiel ao persistido")
    void deveContinuarLoteQuandoUmItemFalha() throws Exception {
        UUID id = UUID.randomUUID();
        TransacaoCandidataDTO c0 = candidata(0, new BigDecimal("10.00"), LocalDate.now(), "Item 0");
        TransacaoCandidataDTO c1 = candidata(1, new BigDecimal("20.00"), LocalDate.now(), "Item 1");
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(c0, c1));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));
        when(importacaoRepository.reivindicarParaConfirmacao(eq(id), any(), any(), any(), any())).thenReturn(1);
        // doAnswer em vez de argThat seletivo: strict stubbing trata chamada sem stub correspondente
        // como PotentialStubbingProblem, o que faria o item 0 falhar também.
        doAnswer(inv -> {
            TransacaoCandidataDTO candidata = inv.getArgument(0);
            if (candidata.indice() == 1) throw new RuntimeException("saldo insuficiente");
            return null;
        }).when(itemProcessor).processar(any(), any(), anyLong(), any());

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(id, 10L, null, null, List.of(0, 1));

        var resultado = service.confirmarImportacao(dto, USUARIO_ID);

        assertThat(resultado.criadas()).isEqualTo(1);
        assertThat(resultado.erros()).isEqualTo(1);
        verify(importacaoRepository).finalizarConfirmacao(id, StatusImportacao.CONFIRMADA, StatusImportacao.PROCESSANDO);
    }

    // ─── vínculos de investimento (Fase 2) ────────────────────────────────────

    private TransacaoCandidataDTO candidataAporte(int indice) {
        return new TransacaoCandidataDTO(indice, "Aplicação Cdb", new BigDecimal("500.00"),
                TipoTransacao.DESPESA, LocalDate.now(), false, MetodoPagamento.PIX, null, null,
                true, false, ClassificacaoCandidata.APORTE, null);
    }

    @Test
    @DisplayName("Vínculo de investimento referenciando índice não selecionado é rejeitado antes do claim")
    void deveRejeitarVinculoComIndiceNaoSelecionado() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(candidataAporte(0)));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));

        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(5, 30L, true); // índice 5 não está selecionado
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                id, 10L, null, null, List.of(0), List.of(vinculo), List.of());

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(importacaoRepository, never()).reivindicarParaConfirmacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Vínculos duplicados para o mesmo índice são rejeitados (nunca last-wins)")
    void deveRejeitarVinculosDuplicadosParaMesmoIndice() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(candidataAporte(0)));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));

        VinculoInvestimentoDTO v1 = new VinculoInvestimentoDTO(0, 30L, true);
        VinculoInvestimentoDTO v2 = new VinculoInvestimentoDTO(0, 31L, false);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                id, 10L, null, null, List.of(0), List.of(v1, v2), List.of());

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(importacaoRepository, never()).reivindicarParaConfirmacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Índice de transferência fora dos selecionados é rejeitado")
    void deveRejeitarIndiceDeTransferenciaNaoSelecionado() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(
                candidata(0, new BigDecimal("10.00"), LocalDate.now(), "Item 0")));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                id, 10L, null, null, List.of(0), List.of(), List.of(9));

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("Ajuste de linha (método/categoria) referenciando índice não selecionado é rejeitado antes do claim")
    void deveRejeitarAjusteDeLinhaComIndiceNaoSelecionado() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(
                candidata(0, new BigDecimal("10.00"), LocalDate.now(), "Item 0")));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));

        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(9, null, 30L); // índice 9 não está selecionado
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                id, 10L, null, null, List.of(0), List.of(), List.of(), List.of(ajuste));

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(importacaoRepository, never()).reivindicarParaConfirmacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ajustes de linha duplicados para o mesmo índice são rejeitados (nunca last-wins)")
    void deveRejeitarAjustesDeLinhaDuplicadosParaMesmoIndice() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(
                candidata(0, new BigDecimal("10.00"), LocalDate.now(), "Item 0")));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));

        AjusteLinhaDTO a1 = new AjusteLinhaDTO(0, MetodoPagamento.PIX, 30L);
        AjusteLinhaDTO a2 = new AjusteLinhaDTO(0, MetodoPagamento.BOLETO, 31L);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                id, 10L, null, null, List.of(0), List.of(), List.of(), List.of(a1, a2));

        assertThatThrownBy(() -> service.confirmarImportacao(dto, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(importacaoRepository, never()).reivindicarParaConfirmacao(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Snapshot de patrimônio é disparado uma única vez quando o lote contém APORTE/RESGATE bem-sucedido")
    void deveDispararSnapshotUnicoQuandoHaMovimentoDeInvestimento() throws Exception {
        UUID id = UUID.randomUUID();
        TransacaoCandidataDTO aporte = candidataAporte(0);
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(aporte));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));
        when(importacaoRepository.reivindicarParaConfirmacao(eq(id), any(), any(), any(), any())).thenReturn(1);

        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(0, 30L, true);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                id, 10L, null, null, List.of(0), List.of(vinculo), List.of());

        service.confirmarImportacao(dto, USUARIO_ID);

        verify(patrimonioHistoricoService, times(1)).atualizarSnapshotUsuarioHoje(USUARIO_ID);
    }

    @Test
    @DisplayName("Snapshot de patrimônio NÃO é disparado quando o lote não tem candidatas de investimento")
    void naoDeveDispararSnapshotSemMovimentoDeInvestimento() throws Exception {
        UUID id = UUID.randomUUID();
        TransacaoCandidataDTO c0 = candidata(0, new BigDecimal("10.00"), LocalDate.now(), "Item 0");
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(c0));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));
        when(importacaoRepository.reivindicarParaConfirmacao(eq(id), any(), any(), any(), any())).thenReturn(1);

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(id, 10L, null, null, List.of(0));

        service.confirmarImportacao(dto, USUARIO_ID);

        verifyNoInteractions(patrimonioHistoricoService);
    }

    @Test
    @DisplayName("Snapshot NÃO é disparado quando o único APORTE selecionado falha (erro de item)")
    void naoDeveDispararSnapshotQuandoAporteFalha() throws Exception {
        UUID id = UUID.randomUUID();
        TransacaoCandidataDTO aporte = candidataAporte(0);
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson(aporte));
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));
        when(importacaoRepository.reivindicarParaConfirmacao(eq(id), any(), any(), any(), any())).thenReturn(1);
        doThrow(new RegraDeNegocioException("error.importacao.vinculo_obrigatorio", "sem vínculo"))
                .when(itemProcessor).processar(any(), any(), anyLong(), any());

        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(0, 30L, true);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                id, 10L, null, null, List.of(0), List.of(vinculo), List.of());

        var resultado = service.confirmarImportacao(dto, USUARIO_ID);

        assertThat(resultado.erros()).isEqualTo(1);
        verifyNoInteractions(patrimonioHistoricoService);
    }

    // ─── cancelarImportacao ────────────────────────────────────────────────────

    @Test
    @DisplayName("Cancelar sessão CONFIRMADA é rejeitado")
    void deveRejeitarCancelarSessaoConfirmada() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        sessao.setStatus(StatusImportacao.CONFIRMADA);
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> service.cancelarImportacao(id, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
        verify(importacaoRepository, never()).cancelarSePendente(any(), any(), any());
    }

    @Test
    @DisplayName("CONCORRÊNCIA: cancelar sessão PROCESSANDO (confirmação em andamento) é rejeitado")
    void deveRejeitarCancelarSessaoProcessando() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        sessao.setStatus(StatusImportacao.PROCESSANDO);
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> service.cancelarImportacao(id, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("sendo confirmada");
        verify(importacaoRepository, never()).cancelarSePendente(any(), any(), any());
    }

    @Test
    @DisplayName("CONCORRÊNCIA: corrida entre cancelar e confirmar — cancelarSePendente retornando 0 é tratado como erro, não como sucesso silencioso")
    void deveTratarCorridaDeCancelamentoComoErro() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));
        when(importacaoRepository.cancelarSePendente(eq(id), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.cancelarImportacao(id, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("Cancelar sessão PENDENTE com sucesso invoca a transição atômica")
    void deveCancelarSessaoPendenteComSucesso() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        when(importacaoRepository.findById(id)).thenReturn(Optional.of(sessao));
        when(importacaoRepository.cancelarSePendente(eq(id), any(), any())).thenReturn(1);

        service.cancelarImportacao(id, USUARIO_ID);

        verify(importacaoRepository).cancelarSePendente(id, StatusImportacao.CANCELADA, StatusImportacao.PENDENTE);
    }

    // ─── desfazerImportacao ────────────────────────────────────────────────────

    @Test
    @DisplayName("Desfazer sessão inexistente → RecursoNaoEncontradoException")
    void deveFalharAoDesfazerSessaoInexistente() {
        UUID id = UUID.randomUUID();
        when(importacaoRepository.findByIdWithLock(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desfazerImportacao(id, USUARIO_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("SEGURANÇA (IDOR): usuário B não desfaz sessão confirmada pertencente ao usuário A")
    void deveFalharAoDesfazerSessaoDeOutroUsuario() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, OUTRO_USUARIO_ID, candidatasJson());
        sessao.setStatus(StatusImportacao.CONFIRMADA);
        when(importacaoRepository.findByIdWithLock(id)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> service.desfazerImportacao(id, USUARIO_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(movimentacaoFinanceiraService);
    }

    @Test
    @DisplayName("Desfazer sessão não confirmada é rejeitado")
    void deveRejeitarDesfazerSessaoNaoConfirmada() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        when(importacaoRepository.findByIdWithLock(id)).thenReturn(Optional.of(sessao));

        assertThatThrownBy(() -> service.desfazerImportacao(id, USUARIO_ID))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("CONCORRÊNCIA: status é marcado CANCELADA sob o lock antes de reverter — impede reversão dupla concorrente")
    void deveMarcarCanceladaAntesDeReverterEfeitos() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        sessao.setStatus(StatusImportacao.CONFIRMADA);
        when(importacaoRepository.findByIdWithLock(id)).thenReturn(Optional.of(sessao));

        TransacaoEntity t1 = new TransacaoEntity();
        t1.setId(100L);
        t1.setAtivo(true);
        when(transacaoRepository.findByImportacaoIdAndUsuarioId(id, USUARIO_ID)).thenReturn(List.of(t1));

        var resultado = service.desfazerImportacao(id, USUARIO_ID);

        assertThat(resultado.excluidas()).isEqualTo(1);
        assertThat(sessao.getStatus()).isEqualTo(StatusImportacao.CANCELADA);
        assertThat(t1.isAtivo()).isFalse();
        verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(t1, USUARIO_ID);

        InOrder ordem = inOrder(importacaoRepository, transacaoRepository);
        ordem.verify(importacaoRepository).save(sessao);
        ordem.verify(transacaoRepository).findByImportacaoIdAndUsuarioId(id, USUARIO_ID);
    }

    @Test
    @DisplayName("Desfazer transação com movimentação de investimento vinculada delega a excluirMovimentacaoPorTransacao — não chama o caminho genérico")
    void deveDelegarParaInvestimentoServiceQuandoHaMovimentacaoVinculada() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        sessao.setStatus(StatusImportacao.CONFIRMADA);
        when(importacaoRepository.findByIdWithLock(id)).thenReturn(Optional.of(sessao));

        TransacaoEntity t1 = new TransacaoEntity();
        t1.setId(100L);
        t1.setAtivo(true);
        when(transacaoRepository.findByImportacaoIdAndUsuarioId(id, USUARIO_ID)).thenReturn(List.of(t1));

        MovimentacaoInvestimentoEntity mov = new MovimentacaoInvestimentoEntity();
        mov.setTransacaoId(100L);
        when(movimentacaoInvestimentoRepository.findByTransacaoIdInAndUsuarioIdAndAtivoTrue(any(), eq(USUARIO_ID)))
                .thenReturn(List.of(mov));

        var resultado = service.desfazerImportacao(id, USUARIO_ID);

        assertThat(resultado.excluidas()).isEqualTo(1);
        verify(investimentoService).excluirMovimentacaoPorTransacao(mov, USUARIO_ID);
        verifyNoInteractions(movimentacaoFinanceiraService);
        // t1 não é tocado diretamente pelo ImportacaoService neste caminho — quem marca
        // ativo=false é o excluirMovimentacaoInterno, dentro da transação delegada.
        verify(transacaoRepository, never()).save(t1);
    }

    @Test
    @DisplayName("REGRESSÃO: desfazer reverte estornos (RECEITA de cartão) ANTES das despesas — a ordem inversa dispararia reducao_abaixo_do_pago")
    void desfazerReverteEstornosDeCartaoPrimeiro() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        sessao.setStatus(StatusImportacao.CONFIRMADA);
        when(importacaoRepository.findByIdWithLock(id)).thenReturn(Optional.of(sessao));

        CartaoEntity cartao = new CartaoEntity();
        TransacaoEntity despesa = new TransacaoEntity();
        despesa.setId(200L);
        despesa.setTipo(TipoTransacao.DESPESA);
        despesa.setCartao(cartao);
        despesa.setAtivo(true);
        TransacaoEntity estorno = new TransacaoEntity();
        estorno.setId(201L);
        estorno.setTipo(TipoTransacao.RECEITA);
        estorno.setCartao(cartao);
        estorno.setAtivo(true);
        // Retorno do banco na ordem de inserção (despesa primeiro) — o service DEVE reordenar.
        when(transacaoRepository.findByImportacaoIdAndUsuarioId(id, USUARIO_ID))
                .thenReturn(List.of(despesa, estorno));

        service.desfazerImportacao(id, USUARIO_ID);

        InOrder ordem = inOrder(movimentacaoFinanceiraService);
        ordem.verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(estorno, USUARIO_ID);
        ordem.verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(despesa, USUARIO_ID);
    }

    @Test
    @DisplayName("Desfazer com transações mistas (normal + investimento) roteia cada uma pelo caminho correto")
    void deveRotearCadaTransacaoPeloCaminhoCorretoNoDesfazerMisto() throws Exception {
        UUID id = UUID.randomUUID();
        ImportacaoEntity sessao = sessaoPendente(id, USUARIO_ID, candidatasJson());
        sessao.setStatus(StatusImportacao.CONFIRMADA);
        when(importacaoRepository.findByIdWithLock(id)).thenReturn(Optional.of(sessao));

        TransacaoEntity normal = new TransacaoEntity();
        normal.setId(101L);
        normal.setAtivo(true);
        TransacaoEntity deInvestimento = new TransacaoEntity();
        deInvestimento.setId(102L);
        deInvestimento.setAtivo(true);
        when(transacaoRepository.findByImportacaoIdAndUsuarioId(id, USUARIO_ID))
                .thenReturn(List.of(normal, deInvestimento));

        MovimentacaoInvestimentoEntity mov = new MovimentacaoInvestimentoEntity();
        mov.setTransacaoId(102L);
        when(movimentacaoInvestimentoRepository.findByTransacaoIdInAndUsuarioIdAndAtivoTrue(any(), eq(USUARIO_ID)))
                .thenReturn(List.of(mov));

        var resultado = service.desfazerImportacao(id, USUARIO_ID);

        assertThat(resultado.excluidas()).isEqualTo(2);
        assertThat(normal.isAtivo()).isFalse();
        verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(normal, USUARIO_ID);
        verify(investimentoService).excluirMovimentacaoPorTransacao(mov, USUARIO_ID);
        verify(movimentacaoFinanceiraService, never()).desfazerEfeitoFinanceiro(deInvestimento, USUARIO_ID);
    }
}
