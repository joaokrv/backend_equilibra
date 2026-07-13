package org.app_financeiro.backend;

import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.AjusteLinhaDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.VinculoInvestimentoDTO;
import org.app_financeiro.backend.dto.request.CategoriaRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.ImportacaoEntity;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.StatusImportacao;
import org.app_financeiro.backend.enums.TipoInvestimento;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.ImportacaoRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.app_financeiro.backend.service.ImportacaoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo completo da importação contra Postgres real (Testcontainers):
 * upload CSV → revisão → confirmação → efeitos financeiros → desfazer.
 * Cobre as garantias de concorrência da Fase 0 (claim atômico, lock pessimista)
 * com corridas reais de threads, e os guards de segurança/anti-abuso.
 */
@DisplayName("Importação de Documentos — Integração")
class ImportacaoIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ImportacaoService importacaoService;
    @Autowired private ImportacaoRepository importacaoRepository;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private ContaRepository contaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private InvestimentoRepository investimentoRepository;

    private String token;
    private Long usuarioId;
    private Long contaId;

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @BeforeEach
    void setUp() throws Exception {
        limparTodasAsTabelas();
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        token = setupUsuarioVerificado("Import Tester", "import@equilibra.test", "Import@123Secure");
        usuarioId = usuarioRepository.findByEmail("import@equilibra.test")
                .map(UsuarioEntity::getId).orElseThrow();

        ContaRegistroRequestDTO contaReq = new ContaRegistroRequestDTO("Conta Importação", new BigDecimal("1000.00"));
        MvcResult res = mockMvc.perform(post("/api/contas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contaReq)))
                .andExpect(status().isCreated())
                .andReturn();
        contaId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @AfterEach
    void tearDown() {
        limparTodasAsTabelas();
    }

    // ─── Fixtures ──────────────────────────────────────────────────────────────

    /** CSV Sicredi anonimizado: 5 linhas de preamble + header + linhas de dados. */
    private MockMultipartFile csv(String... linhasDeDados) {
        StringBuilder sb = new StringBuilder();
        sb.append("Extrato Conta Corrente\n;;;\nAssociado: FULANO DE TAL\nCooperativa: 0000\nConta: 00000-0\n");
        sb.append("Data Lançamento;Histórico;Descrição;Valor;Saldo\n");
        for (String linha : linhasDeDados) {
            sb.append(linha).append('\n');
        }
        return new MockMultipartFile("arquivo", "extrato.csv", "text/csv",
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String linhaDespesa(LocalDate data, String descricao, String valor) {
        return data.format(DATA_BR) + ";;" + descricao + ";-" + valor + ";0,00";
    }

    private String linhaReceita(LocalDate data, String descricao, String valor) {
        return data.format(DATA_BR) + ";;" + descricao + ";" + valor + ";0,00";
    }

    /** Histórico "Aplicação" → CsvExtratoParser classifica automaticamente como APORTE. */
    private String linhaAporte(LocalDate data, String descricao, String valor) {
        return data.format(DATA_BR) + ";Aplicação;" + descricao + ";-" + valor + ";0,00";
    }

    /** Histórico "Resgate" → CsvExtratoParser classifica automaticamente como RESGATE. */
    private String linhaResgate(LocalDate data, String descricao, String valor) {
        return data.format(DATA_BR) + ";Resgate;" + descricao + ";" + valor + ";0,00";
    }

    /** Investimento com valorInicial=0 — não movimenta a conta na criação, mantém a matemática do teste simples. */
    private Long criarInvestimento(String descricao) throws Exception {
        InvestimentoRegistroRequestDTO dto = new InvestimentoRegistroRequestDTO(
                descricao, BigDecimal.ZERO, null, contaId, null, TipoInvestimento.CDB, null);
        MvcResult res = mockMvc.perform(post("/api/investimentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private BigDecimal valorAtualInvestimento(Long investimentoId) {
        return investimentoRepository.findById(investimentoId).map(InvestimentoEntity::getValorAtual).orElseThrow();
    }

    private Long criarCategoria(String nome, org.app_financeiro.backend.enums.TipoTransacao tipo) throws Exception {
        CategoriaRegistroRequestDTO dto = new CategoriaRegistroRequestDTO(nome, tipo);
        MvcResult res = mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private UUID iniciarImportacaoViaHttp(MockMultipartFile arquivo) throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/importacao")
                        .file(arquivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString())
                .get("importacaoId").asText());
    }

    private ConfirmarImportacaoRequestDTO confirmacao(UUID importacaoId, List<Integer> indices) {
        return new ConfirmarImportacaoRequestDTO(importacaoId, contaId, null, null, indices);
    }

    private BigDecimal saldoAtual() {
        return contaRepository.findById(contaId).map(ContaEntity::getSaldo).orElseThrow();
    }

    // ─── Fluxo completo ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Fluxo completo: upload → confirmação → saldo ajustado → desfazer → saldo restaurado")
    void fluxoCompletoDeImportacao() throws Exception {
        LocalDate hoje = LocalDate.now();
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(hoje, "Compra Mercado Teste", "150,50"),
                linhaReceita(hoje, "Pix Recebido Teste", "200,00")));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmacao(importacaoId, List.of(0, 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(2))
                .andExpect(jsonPath("$.erros").value(0));

        // PIX → status PAGO → saldo movimentado: 1000 − 150.50 + 200 = 1049.50
        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1049.50"));

        List<TransacaoEntity> transacoes = transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId);
        assertThat(transacoes).hasSize(2);
        assertThat(transacoes).allSatisfy(t -> {
            assertThat(t.getOrigem()).isEqualTo("IMPORTACAO");
            assertThat(t.getImportacaoId()).isEqualTo(importacaoId);
        });

        ImportacaoEntity sessao = importacaoRepository.findById(importacaoId).orElseThrow();
        assertThat(sessao.getStatus()).isEqualTo(StatusImportacao.CONFIRMADA);
        assertThat(sessao.getCandidatas()).isNull();

        mockMvc.perform(delete("/api/importacao/{id}/transacoes", importacaoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excluidas").value(2));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId))
                .allSatisfy(t -> assertThat(t.isAtivo()).isFalse());
    }

    @Test
    @DisplayName("Confirmação parcial: somente os índices selecionados viram transações")
    void confirmacaoParcialImportaSomenteSelecionados() throws Exception {
        LocalDate hoje = LocalDate.now();
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(hoje, "Compra Incluida", "50,00"),
                linhaDespesa(hoje, "Compra Ignorada", "999,00")));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmacao(importacaoId, List.of(0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(1));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("950.00"));
    }

    @Test
    @DisplayName("Mesmo documento importado duas vezes: segunda sessão marca duplicatas detectadas")
    void deveMarcarDuplicatasNaSegundaImportacao() throws Exception {
        LocalDate hoje = LocalDate.now();
        MockMultipartFile arquivo = csv(linhaDespesa(hoje, "Compra Duplicada Unica", "77,70"));

        UUID primeira = iniciarImportacaoViaHttp(arquivo);
        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmacao(primeira, List.of(0)))))
                .andExpect(status().isOk());

        MvcResult res = mockMvc.perform(multipart("/api/importacao")
                        .file(csv(linhaDespesa(hoje, "Compra Duplicada Unica", "77,70")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalDuplicatas").value(1))
                .andReturn();

        boolean duplicata = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("candidatas").get(0).get("duplicataDetectada").asBoolean();
        assertThat(duplicata).isTrue();
    }

    // ─── Concorrência (Fase 0.A) ───────────────────────────────────────────────

    @Test
    @DisplayName("CONCORRÊNCIA: duas confirmações simultâneas da mesma sessão — exatamente uma vence, sem transação duplicada")
    void confirmacoesConcorrentesNaoDuplicamTransacoes() throws Exception {
        LocalDate hoje = LocalDate.now();
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(hoje, "Compra Corrida A", "100,00"),
                linhaDespesa(hoje, "Compra Corrida B", "50,00")));

        ConfirmarImportacaoRequestDTO dto = confirmacao(importacaoId, List.of(0, 1));

        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger rejeitadas = new AtomicInteger();
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    largada.await();
                    importacaoService.confirmarImportacao(dto, usuarioId);
                    sucessos.incrementAndGet();
                } catch (RegraDeNegocioException e) {
                    rejeitadas.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    chegada.countDown();
                }
            });
        }
        largada.countDown();
        assertThat(chegada.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(rejeitadas.get()).isEqualTo(1);
        // Efeito financeiro aplicado exatamente uma vez: 1000 − 100 − 50 = 850
        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("850.00"));
        assertThat(transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId)).hasSize(2);
    }

    @Test
    @DisplayName("CONCORRÊNCIA: dois desfazer simultâneos — reversão aplicada exatamente uma vez")
    void desfazerConcorrenteNaoReverteEmDobro() throws Exception {
        LocalDate hoje = LocalDate.now();
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(hoje, "Compra Para Desfazer", "300,00")));

        importacaoService.confirmarImportacao(confirmacao(importacaoId, List.of(0)), usuarioId);
        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("700.00"));

        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger rejeitadas = new AtomicInteger();
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    largada.await();
                    importacaoService.desfazerImportacao(importacaoId, usuarioId);
                    sucessos.incrementAndGet();
                } catch (RuntimeException e) {
                    rejeitadas.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    chegada.countDown();
                }
            });
        }
        largada.countDown();
        assertThat(chegada.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(rejeitadas.get()).isEqualTo(1);
        // Se a reversão rodasse duas vezes o saldo iria a 1300 — deve voltar exatamente a 1000.
        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Confirmar a mesma sessão duas vezes em sequência: segunda recebe 422")
    void segundaConfirmacaoSequencialEhRejeitada() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Compra Unica", "10,00")));
        String body = objectMapper.writeValueAsString(confirmacao(importacaoId, List.of(0)));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("990.00"));
    }

    @Test
    @DisplayName("Sessão presa em PROCESSANDO há mais de 10 min pode ser re-reivindicada; itens já criados não duplicam")
    void deveReivindicarSessaoAbandonadaAposLimite() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Compra Crash Recovery", "25,00")));

        // Simula crash: sessão ficou PROCESSANDO com claim antigo (>10 min)
        ImportacaoEntity sessao = importacaoRepository.findById(importacaoId).orElseThrow();
        sessao.setStatus(StatusImportacao.PROCESSANDO);
        sessao.setProcessandoEm(java.time.LocalDateTime.now().minusMinutes(15));
        importacaoRepository.save(sessao);

        var resultado = importacaoService.confirmarImportacao(confirmacao(importacaoId, List.of(0)), usuarioId);

        assertThat(resultado.criadas()).isEqualTo(1);
        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("975.00"));
        assertThat(importacaoRepository.findById(importacaoId).orElseThrow().getStatus())
                .isEqualTo(StatusImportacao.CONFIRMADA);
    }

    @Test
    @DisplayName("Sessão PROCESSANDO recente (confirmação em andamento) NÃO pode ser re-reivindicada")
    void naoDeveReivindicarSessaoProcessandoRecente() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Compra Em Processamento", "25,00")));

        ImportacaoEntity sessao = importacaoRepository.findById(importacaoId).orElseThrow();
        sessao.setStatus(StatusImportacao.PROCESSANDO);
        sessao.setProcessandoEm(java.time.LocalDateTime.now());
        importacaoRepository.save(sessao);

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmacao(importacaoId, List.of(0)))))
                .andExpect(status().isUnprocessableEntity());

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // ─── Cancelamento ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cancelar sessão PENDENTE impede confirmação posterior (404) e não movimenta saldo")
    void cancelarImpedeConfirmacaoPosterior() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Compra Cancelada", "10,00")));

        mockMvc.perform(delete("/api/importacao/{id}", importacaoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmacao(importacaoId, List.of(0)))))
                .andExpect(status().isNotFound());

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    // ─── Anti-abuso / validações de borda ─────────────────────────────────────

    @Test
    @DisplayName("ANTI-ABUSO: anoPresumido no futuro → 422 sem processar nada")
    void anoPresumidoNoFuturoEhRejeitado() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Compra Ano Futuro", "10,00")));

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, LocalDate.now().getYear() + 1, List.of(0));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("ANTI-ABUSO: conta e cartão simultâneos na confirmação → 422")
    void contaECartaoSimultaneosSaoRejeitados() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Compra XOR", "10,00")));

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, 999L, null, List.of(0));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("ANTI-ABUSO: arquivo com formato não suportado → 422")
    void formatoNaoSuportadoEhRejeitado() throws Exception {
        MockMultipartFile png = new MockMultipartFile("arquivo", "malicioso.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A});

        mockMvc.perform(multipart("/api/importacao")
                        .file(png)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Índices selecionados fora do documento são simplesmente ignorados (sem 500)")
    void indicesInexistentesSaoIgnorados() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Compra Indice Valido", "10,00")));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmacao(importacaoId, List.of(0, 98, 99)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(1))
                .andExpect(jsonPath("$.erros").value(0));
    }

    @Test
    @DisplayName("Item com saldo insuficiente conta como erro sem abortar o lote (REQUIRES_NEW)")
    void itemComSaldoInsuficienteNaoAbortaLote() throws Exception {
        LocalDate hoje = LocalDate.now();
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(hoje, "Compra Cabivel", "100,00"),
                linhaDespesa(hoje, "Compra Impagavel", "99999,00")));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmacao(importacaoId, List.of(0, 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(1))
                .andExpect(jsonPath("$.erros").value(1));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    // ─── Investimentos (Fase 2) ────────────────────────────────────────────────

    @Test
    @DisplayName("Aporte vinculado com atualizarValor=true: saldo da conta cai e valorAtual do investimento sobe")
    void aporteVinculadoComAtualizarValorAjustaContaEInvestimento() throws Exception {
        Long investimentoId = criarInvestimento("CDB Banco Teste");
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaAporte(LocalDate.now(), "Cdb Banco Teste", "500,00")));

        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(0, investimentoId, true);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, null, List.of(0), List.of(vinculo), List.of());

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(1))
                .andExpect(jsonPath("$.erros").value(0));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(valorAtualInvestimento(investimentoId)).isEqualByComparingTo(new BigDecimal("500.00"));

        TransacaoEntity transacao = transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId).get(0);
        assertThat(transacao.isTransferencia()).isTrue();
    }

    @Test
    @DisplayName("Aporte vinculado com atualizarValor=false: saldo da conta cai, valorAtual do investimento NÃO muda")
    void aporteVinculadoSemAtualizarValorSoMovimentaConta() throws Exception {
        Long investimentoId = criarInvestimento("CDB Ja Cadastrado");
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaAporte(LocalDate.now(), "Cdb Ja Cadastrado", "500,00")));

        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(0, investimentoId, false);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, null, List.of(0), List.of(vinculo), List.of());

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(1));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(valorAtualInvestimento(investimentoId)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Resgate vinculado com atualizarValor=true: conta é creditada e valorAtual do investimento cai")
    void resgateVinculadoComAtualizarValorAjustaContaEInvestimento() throws Exception {
        Long investimentoId = criarInvestimento("CDB Com Saldo");
        // Aporte manual (fluxo normal) para o investimento ter saldo suficiente para o resgate.
        mockMvc.perform(post("/api/investimentos/{id}/depositar", investimentoId)
                        .header("Authorization", "Bearer " + token)
                        .param("valor", "1000.00")
                        .param("contaId", contaId.toString()))
                .andExpect(status().isOk());
        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("0.00"));

        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaResgate(LocalDate.now(), "Cdb Com Saldo", "300,00")));
        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(0, investimentoId, true);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, null, List.of(0), List.of(vinculo), List.of());

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(1));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(valorAtualInvestimento(investimentoId)).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    @DisplayName("APORTE selecionado SEM vínculo vira erro de item — nunca é importado como despesa comum")
    void aporteSemVinculoVirsErroDeItem() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaAporte(LocalDate.now(), "Cdb Sem Vinculo", "500,00")));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmacao(importacaoId, List.of(0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(0))
                .andExpect(jsonPath("$.erros").value(1));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId)).isEmpty();
    }

    @Test
    @DisplayName("SEGURANÇA (IDOR): vincular investimento de outro usuário vira erro de item, não expõe nem move dinheiro")
    void vinculoComInvestimentoDeOutroUsuarioVirsErroDeItem() throws Exception {
        String tokenOutro = setupUsuarioVerificado("Outro Usuario", "outro-import@equilibra.test", "Outro@123Secure");
        MvcResult contaOutroRes = mockMvc.perform(post("/api/contas")
                        .header("Authorization", "Bearer " + tokenOutro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ContaRegistroRequestDTO("Conta Outro", BigDecimal.ZERO))))
                .andExpect(status().isCreated())
                .andReturn();
        Long contaOutroId = objectMapper.readTree(contaOutroRes.getResponse().getContentAsString()).get("id").asLong();
        InvestimentoRegistroRequestDTO dtoInvOutro = new InvestimentoRegistroRequestDTO(
                "CDB De Outro Usuario", BigDecimal.ZERO, null, contaOutroId, null, TipoInvestimento.CDB, null);
        MvcResult invOutroRes = mockMvc.perform(post("/api/investimentos")
                        .header("Authorization", "Bearer " + tokenOutro)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dtoInvOutro)))
                .andExpect(status().isCreated())
                .andReturn();
        Long investimentoDeOutro = objectMapper.readTree(invOutroRes.getResponse().getContentAsString()).get("id").asLong();

        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaAporte(LocalDate.now(), "Tentativa Idor", "500,00")));
        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(0, investimentoDeOutro, true);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, null, List.of(0), List.of(vinculo), List.of());

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(0))
                .andExpect(jsonPath("$.erros").value(1));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(valorAtualInvestimento(investimentoDeOutro)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Índice marcado em indicesTransferencia é importado como transferência interna (isTransferencia=true), independente da classificação sugerida")
    void indiceMarcadoComoTransferenciaRoteiaComoTransferenciaInterna() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaReceita(LocalDate.now(), "Pix Recebido Mesmo Titular", "300,00")));

        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, null, List.of(0), List.of(), List.of(0));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(1));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1300.00"));
        TransacaoEntity transacao = transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId).get(0);
        assertThat(transacao.isTransferencia()).isTrue();
    }

    @Test
    @DisplayName("Vínculo referenciando índice não selecionado é rejeitado com 422 antes de processar qualquer item")
    void vinculoComIndiceNaoSelecionadoEhRejeitado() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaAporte(LocalDate.now(), "Cdb Indice Invalido", "500,00")));

        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(7, 999L, true); // 7 não foi selecionado
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, null, List.of(0), List.of(vinculo), List.of());

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Desfazer importação com aporte reverte saldo da conta E valorAtual do investimento")
    void desfazerImportacaoComAporteReverteContaEInvestimento() throws Exception {
        Long investimentoId = criarInvestimento("CDB Para Desfazer");
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaAporte(LocalDate.now(), "Cdb Para Desfazer", "500,00")));
        VinculoInvestimentoDTO vinculo = new VinculoInvestimentoDTO(0, investimentoId, true);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, null, List.of(0), List.of(vinculo), List.of());

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(valorAtualInvestimento(investimentoId)).isEqualByComparingTo(new BigDecimal("500.00"));

        mockMvc.perform(delete("/api/importacao/{id}/transacoes", importacaoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excluidas").value(1));

        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(valorAtualInvestimento(investimentoId)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── Método de pagamento ────────────────────────────────────────────────────

    @Test
    @DisplayName("MÉTODO: boleto em extrato de conta é gravado como BOLETO mas DEBITA o saldo (status PAGO, não pendente)")
    void boletoEmExtratoDeContaDebitaSaldoComoPago() throws Exception {
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Pagamento Boleto Energia", "100,00")));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmacao(importacaoId, List.of(0)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(1));

        // Sem o desacoplamento, BOLETO viraria PENDENTE e o saldo ficaria em 1000.
        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("900.00"));

        TransacaoEntity t = transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId).get(0);
        assertThat(t.getMetodoPagamento()).isEqualTo(org.app_financeiro.backend.enums.MetodoPagamento.BOLETO);
        assertThat(t.getStatus()).isEqualTo(org.app_financeiro.backend.enums.StatusTransacao.PAGO);
    }

    @Test
    @DisplayName("AJUSTE: override de método e categoria do usuário é persistido na transação de conta")
    void ajusteDeMetodoECategoriaEhPersistidoNaTransacaoDeConta() throws Exception {
        Long categoriaId = criarCategoria("Contas Fixas", org.app_financeiro.backend.enums.TipoTransacao.DESPESA);
        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Pagamento Boleto Energia", "100,00")));

        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(0, org.app_financeiro.backend.enums.MetodoPagamento.DINHEIRO, categoriaId);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, null, List.of(0), List.of(), List.of(), List.of(ajuste));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(1));

        TransacaoEntity t = transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId).get(0);
        assertThat(t.getMetodoPagamento()).isEqualTo(org.app_financeiro.backend.enums.MetodoPagamento.DINHEIRO);
        assertThat(t.getCategoria()).isNotNull();
        assertThat(t.getCategoria().getId()).isEqualTo(categoriaId);
        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    @DisplayName("SEGURANÇA: categoria de outro usuário na confirmação vira erro de item, lote não é derrubado")
    void ajusteDeCategoriaDeOutroUsuarioViraErroDeItem() throws Exception {
        String outroToken = setupUsuarioVerificado("Outro Usuario", "outro@equilibra.test", "Outro@123Secure");
        MvcResult catRes = mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + outroToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CategoriaRegistroRequestDTO("Categoria Alheia", org.app_financeiro.backend.enums.TipoTransacao.DESPESA))))
                .andExpect(status().isCreated())
                .andReturn();
        Long categoriaDeOutroUsuario = objectMapper.readTree(catRes.getResponse().getContentAsString()).get("id").asLong();

        UUID importacaoId = iniciarImportacaoViaHttp(csv(
                linhaDespesa(LocalDate.now(), "Compra Mercado", "50,00")));

        AjusteLinhaDTO ajuste = new AjusteLinhaDTO(0, null, categoriaDeOutroUsuario);
        ConfirmarImportacaoRequestDTO dto = new ConfirmarImportacaoRequestDTO(
                importacaoId, contaId, null, null, List.of(0), List.of(), List.of(), List.of(ajuste));

        mockMvc.perform(post("/api/importacao/confirmar")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criadas").value(0))
                .andExpect(jsonPath("$.erros").value(1));

        assertThat(transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId)).isEmpty();
        assertThat(saldoAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }
}
