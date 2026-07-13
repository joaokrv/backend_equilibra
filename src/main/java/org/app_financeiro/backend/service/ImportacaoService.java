package org.app_financeiro.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO;
import org.app_financeiro.backend.dto.importacao.ImportacaoIniciadaDTO;
import org.app_financeiro.backend.dto.importacao.TransacaoCandidataDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.VinculoInvestimentoDTO;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO.AjusteLinhaDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.ImportacaoEntity;
import org.app_financeiro.backend.entity.MovimentacaoInvestimentoEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.ClassificacaoCandidata;
import org.app_financeiro.backend.enums.FormatoDetectado;
import org.app_financeiro.backend.enums.StatusImportacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.OperacaoNaoPermitidaException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.repository.ImportacaoRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.MovimentacaoInvestimentoRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.util.FaturaDateUtil;
import org.app_financeiro.backend.util.ImportacaoConstantes;
import org.app_financeiro.backend.util.TextoUtil;
import org.app_financeiro.backend.util.ValidacaoRecursoUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportacaoService {

    private static final int MAX_DESCRICAO = 255;
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("9999999.99");
    private static final LocalDate DATA_MINIMA = LocalDate.of(1995, 1, 1);
    private static final Duration LIMITE_PROCESSANDO = Duration.ofMinutes(10);

    private static final String PROMPT_FATURA_PDF = """
            Extraia TODAS as transações desta fatura de cartão de crédito.
            Ignore linhas de pagamento de fatura (ex: "Pagamento De Fatura", "Crédito de Pagamento") e informações institucionais (IOF, encargos, resumo de limite).
            Para cada transação retorne um objeto JSON com os campos:
            - indice (int, sequencial a partir de 0)
            - descricao (string, nome do estabelecimento ou descrição do lançamento)
            - valor (decimal positivo, em reais)
            - tipo: "DESPESA" para compras; "RECEITA" para estornos ou créditos
            - data (string OBRIGATORIAMENTE no formato ISO "yyyy-MM-dd"; se o ano não estiver no documento, use 2000)
            - dataPresumida (boolean: true se você usou o ano 2000 porque o ano não estava explícito)
            - numeroParcela (int ou null — número da parcela atual, ex: 1, 2, 3)
            - totalParcelas (int ou null — total de parcelas, ex: 12)
            - suspeita (boolean: false para transações normais de compra)
            Retorne APENAS um JSON válido com o campo "transacoes": [array de objetos acima]. Sem texto adicional.
            """;

    /** Usado para extrato em PDF e como fallback para CSVs de estrutura não reconhecida. */
    private static final String PROMPT_EXTRATO = """
            Extraia TODAS as transações deste extrato bancário.
            Ignore linhas de saldo inicial, saldo final e totalizadores.
            Para cada transação retorne um objeto JSON com os campos:
            - indice (int, sequencial a partir de 0)
            - descricao (string, descrição do lançamento)
            - valor (decimal positivo, em reais)
            - tipo: "DESPESA" para saídas/débitos; "RECEITA" para entradas/créditos
            - data (string OBRIGATORIAMENTE no formato ISO "yyyy-MM-dd")
            - dataPresumida (boolean: false — extratos bancários sempre têm o ano)
            - numeroParcela (null)
            - totalParcelas (null)
            - suspeita (boolean: true para aplicações/resgates de investimento (CDB, poupança, fundo) e pagamentos de fatura de cartão)
            - classificacao (string: "APORTE" para aplicação em investimento; "RESGATE" para resgate de investimento; "NORMAL" para todo o restante. NUNCA classifique pagamento de fatura de cartão como APORTE/RESGATE — pagamento de fatura é "NORMAL")
            - metodoPagamento (string, um de: "PIX", "TRANSFERENCIA", "BOLETO", "CARTAO_DEBITO", "DINHEIRO"; infira pelo lançamento — "PIX" quando não houver indício claro)
            Retorne APENAS um JSON válido com o campo "transacoes": [array de objetos acima]. Sem texto adicional.
            """;

    private final DocumentoDetector documentoDetector;
    private final CsvExtratoParser csvExtratoParser;
    private final GeminiClient geminiClient;
    private final ImportacaoRepository importacaoRepository;
    private final TransacaoRepository transacaoRepository;
    private final InvestimentoRepository investimentoRepository;
    private final MovimentacaoInvestimentoRepository movimentacaoInvestimentoRepository;
    private final ImportacaoItemProcessor itemProcessor;
    private final FaturaService faturaService;
    private final CartaoService cartaoService;
    private final UsuarioService usuarioService;
    private final MovimentacaoFinanceiraService movimentacaoFinanceiraService;
    private final InvestimentoService investimentoService;
    private final PatrimonioHistoricoService patrimonioHistoricoService;
    private final ObjectMapper objectMapper;

    /**
     * SEM @Transactional (intencional): a extração pode chamar o Gemini (HTTP com timeout de 60s)
     * e uma transação aberta seguraria a conexão do pool durante a chamada inteira — poucos
     * uploads simultâneos esgotariam o Hikari. A única escrita é o save da sessão, atômico sozinho.
     */
    public ImportacaoIniciadaDTO iniciarImportacao(MultipartFile arquivo, Long usuarioId) {
        FormatoDetectado formato = documentoDetector.detectar(arquivo);

        List<TransacaoCandidataDTO> candidatas = enriquecerClassificacao(
                sanearCandidatas(extrairCandidatas(arquivo, formato, usuarioId)), usuarioId);
        List<TransacaoCandidataDTO> comDuplicatas = marcarDuplicatas(candidatas, usuarioId);

        int totalDuplicatas = (int) comDuplicatas.stream().filter(TransacaoCandidataDTO::duplicataDetectada).count();
        boolean contemDataPresumida = comDuplicatas.stream().anyMatch(TransacaoCandidataDTO::dataPresumida);

        ImportacaoEntity sessao = new ImportacaoEntity();
        sessao.setId(UUID.randomUUID());
        sessao.setUsuario(usuarioService.buscarPorIdOuFalhar(usuarioId));
        sessao.setFormatoDetectado(formato);
        sessao.setTotalCandidatas(comDuplicatas.size());
        sessao.setTotalDuplicatas(totalDuplicatas);
        sessao.setCandidatas(serializarCandidatas(comDuplicatas));
        importacaoRepository.save(sessao);

        return new ImportacaoIniciadaDTO(
                sessao.getId(), formato, comDuplicatas,
                comDuplicatas.size(), totalDuplicatas, contemDataPresumida);
    }

    /**
     * SEM @Transactional de lote (intencional): cada item roda em transação própria
     * (REQUIRES_NEW no ImportacaoItemProcessor). Um item que falha não marca
     * rollback-only o lote — os demais commitam e o resultado parcial reportado
     * (criadas/erros) é fiel ao que foi persistido. As faturas históricas são
     * pré-criadas em transações próprias e commitadas antes dos itens, para que
     * as transações REQUIRES_NEW as enxerguem.
     *
     * Claim atômico (reivindicarParaConfirmacao) transiciona PENDENTE→PROCESSANDO em
     * transação própria e curta antes do loop começar: duas requisições concorrentes
     * (duplo clique, retry, duas abas) para a mesma sessão — só uma reivindica, a outra
     * recebe error.importacao.processando sem tocar em nada. Sessão presa em PROCESSANDO
     * por crash no meio do lote é re-reivindicável após LIMITE_PROCESSANDO; o retry é
     * seguro porque a idempotency key determinística bloqueia recriação dos itens já commitados.
     */
    public ImportacaoConfirmadaDTO confirmarImportacao(ConfirmarImportacaoRequestDTO dto, Long usuarioId) {
        ImportacaoEntity sessao = carregarSessaoValidada(dto.importacaoId(), usuarioId);
        if (sessao.getStatus() == StatusImportacao.CONFIRMADA) {
            throw new RegraDeNegocioException(
                    "error.importacao.ja_confirmada",
                    "Esta importação já foi confirmada.");
        }
        ValidacaoRecursoUtil.validarContaXorCartao(dto.contaId(), dto.cartaoId());
        validarAnoPresumido(dto.anoPresumido());
        validarVinculos(dto);

        LocalDateTime agora = LocalDateTime.now();
        int reivindicada = importacaoRepository.reivindicarParaConfirmacao(
                dto.importacaoId(), agora, agora.minus(LIMITE_PROCESSANDO),
                StatusImportacao.PENDENTE, StatusImportacao.PROCESSANDO);
        if (reivindicada == 0) {
            throw new RegraDeNegocioException(
                    "error.importacao.processando",
                    "Esta importação já foi confirmada ou está sendo processada.");
        }

        List<TransacaoCandidataDTO> todas = deserializarCandidatas(sessao.getCandidatas());
        Set<Integer> indicesSelecionados = Set.copyOf(dto.indicesSelecionados());

        // Cartão: despesas antes de receitas — estornos só são aceitos quando o saldo devedor
        // já foi lançado na fatura (error.fatura.estorno_excede). Conta: receitas antes de
        // despesas — créditos primeiro evitam saldo_insuficiente em extrato que se sustenta.
        TipoTransacao primeiro = dto.cartaoId() != null ? TipoTransacao.DESPESA : TipoTransacao.RECEITA;
        List<TransacaoCandidataDTO> selecionadas = todas.stream()
                .filter(c -> indicesSelecionados.contains(c.indice()))
                .map(c -> aplicarAnoPresumido(c, dto.anoPresumido()))
                .sorted(Comparator.comparingInt(c -> c.tipo() == primeiro ? 0 : 1))
                .toList();

        if (dto.cartaoId() != null) {
            precriarFaturasHistoricas(dto.cartaoId(), selecionadas, usuarioId);
        }

        int criadas = 0, erros = 0;
        boolean houveMovimentoDeInvestimento = false;
        for (TransacaoCandidataDTO candidata : selecionadas) {
            try {
                itemProcessor.processar(candidata, dto, usuarioId, sessao.getId());
                criadas++;
                if (candidata.classificacao() == ClassificacaoCandidata.APORTE
                        || candidata.classificacao() == ClassificacaoCandidata.RESGATE) {
                    houveMovimentoDeInvestimento = true;
                }
            } catch (OperacaoNaoPermitidaException e) {
                // Retry pós-crash (re-claim >10min): item já commitado antes da queda. A idempotency
                // key barra a duplicação — contar como erro faria o usuário reimportar o documento.
                log.info("Candidata indice={} já processada em tentativa anterior (idempotência)", candidata.indice());
                criadas++;
            } catch (Exception e) {
                log.warn("Candidata indice={} ignorada durante confirmação: {}", candidata.indice(), e.getMessage());
                erros++;
            }
        }

        // Snapshot único ao final do lote — evita N recálculos de patrimônio num lote com N aportes/resgates.
        if (houveMovimentoDeInvestimento) {
            patrimonioHistoricoService.atualizarSnapshotUsuarioHoje(usuarioId);
        }

        importacaoRepository.finalizarConfirmacao(
                dto.importacaoId(), StatusImportacao.CONFIRMADA, StatusImportacao.PROCESSANDO);

        return new ImportacaoConfirmadaDTO(dto.importacaoId(), criadas, erros);
    }

    /**
     * Vínculos/transferências devem referenciar índices selecionados, sem duplicatas —
     * falha cedo (antes do claim) em vez de deixar o processor descobrir por item.
     */
    private void validarVinculos(ConfirmarImportacaoRequestDTO dto) {
        Set<Integer> selecionados = Set.copyOf(dto.indicesSelecionados());
        Set<Integer> jaVinculados = new HashSet<>();
        for (VinculoInvestimentoDTO vinculo : dto.vinculosInvestimento()) {
            if (!selecionados.contains(vinculo.indice()) || !jaVinculados.add(vinculo.indice())) {
                throw new RegraDeNegocioException(
                        "error.importacao.vinculo_invalido",
                        "Vínculo de investimento referencia um índice inválido ou duplicado.");
            }
        }
        for (Integer indice : dto.indicesTransferencia()) {
            if (!selecionados.contains(indice)) {
                throw new RegraDeNegocioException(
                        "error.importacao.vinculo_invalido",
                        "Índice de transferência não está entre os selecionados.");
            }
        }
        Set<Integer> jaAjustados = new HashSet<>();
        for (AjusteLinhaDTO ajuste : dto.ajustesLinha()) {
            if (!selecionados.contains(ajuste.indice()) || !jaAjustados.add(ajuste.indice())) {
                throw new RegraDeNegocioException(
                        "error.importacao.vinculo_invalido",
                        "Ajuste de linha referencia um índice inválido ou duplicado.");
            }
        }
    }

    /**
     * cancelarSePendente é a transição atômica real: só cancela quem ainda está PENDENTE.
     * Se uma confirmação reivindicar a sessão entre a leitura acima e o UPDATE, o retorno
     * 0 é tratado como "sendo processada agora" — nunca sobrescreve um lote em andamento.
     */
    @Transactional
    public void cancelarImportacao(UUID importacaoId, Long usuarioId) {
        ImportacaoEntity sessao = carregarSessaoValidada(importacaoId, usuarioId);
        if (sessao.getStatus() == StatusImportacao.CONFIRMADA) {
            throw new RegraDeNegocioException(
                    "error.importacao.nao_encontrada",
                    "Esta importação já foi confirmada. Use a exclusão de transações individuais para desfazer.");
        }
        if (sessao.getStatus() == StatusImportacao.PROCESSANDO) {
            throw new RegraDeNegocioException(
                    "error.importacao.processando",
                    "Esta importação está sendo confirmada e não pode ser cancelada agora.");
        }

        int atualizado = importacaoRepository.cancelarSePendente(
                importacaoId, StatusImportacao.CANCELADA, StatusImportacao.PENDENTE);
        if (atualizado == 0) {
            throw new RegraDeNegocioException(
                    "error.importacao.processando",
                    "Esta importação está sendo confirmada e não pode ser cancelada agora.");
        }
    }

    /**
     * Lock pessimista em vez do helper padrão: uma segunda chamada concorrente a desfazer
     * bloqueia em findByIdWithLock até este commit, então enxerga status=CANCELADA (já marcado
     * abaixo, antes da reversão) e falha no guard — impede reverter os mesmos efeitos financeiros
     * duas vezes.
     */
    @Transactional
    public ImportacaoDesfeitaDTO desfazerImportacao(UUID importacaoId, Long usuarioId) {
        ImportacaoEntity sessao = importacaoRepository.findByIdWithLock(importacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão de importação não encontrada."));
        ValidacaoRecursoUtil.validarPropriedade(sessao.getUsuario().getId(), usuarioId, "importação");
        if (sessao.getStatus() != StatusImportacao.CONFIRMADA) {
            throw new RegraDeNegocioException(
                    "error.importacao.nao_encontrada",
                    "Somente importações confirmadas podem ser desfeitas.");
        }

        sessao.setStatus(StatusImportacao.CANCELADA);
        importacaoRepository.save(sessao);

        List<TransacaoEntity> transacoes = new ArrayList<>(
                transacaoRepository.findByImportacaoIdAndUsuarioId(importacaoId, usuarioId));

        // Reversão na ordem inversa da confirmação: em cartão, estornos (RECEITA) voltam antes
        // das despesas — remover a despesa primeiro derrubaria valorTotal abaixo do estorno já
        // abatido e dispararia reducao_abaixo_do_pago, abortando o desfazer inteiro. Em conta,
        // despesas voltam primeiro (creditam saldo) para as receitas poderem ser debitadas.
        transacoes.sort(Comparator.comparingInt(t -> {
            boolean receitaPrimeiro = t.getCartao() != null;
            return (t.getTipo() == TipoTransacao.RECEITA) == receitaPrimeiro ? 0 : 1;
        }));

        Map<Long, MovimentacaoInvestimentoEntity> movimentacoesPorTransacao = transacoes.isEmpty()
                ? Map.of()
                : movimentacaoInvestimentoRepository.findByTransacaoIdInAndUsuarioIdAndAtivoTrue(
                                transacoes.stream().map(TransacaoEntity::getId).toList(), usuarioId)
                        .stream()
                        .collect(Collectors.toMap(MovimentacaoInvestimentoEntity::getTransacaoId, Function.identity()));

        for (TransacaoEntity t : transacoes) {
            MovimentacaoInvestimentoEntity movimentacao = movimentacoesPorTransacao.get(t.getId());
            if (movimentacao != null) {
                // Rota canônica de reversão de investimento: já reverte o efeito financeiro,
                // ajusta valorAtual (quando aplicável) e marca transação + movimentação como inativas.
                investimentoService.excluirMovimentacaoPorTransacao(movimentacao, usuarioId);
            } else {
                movimentacaoFinanceiraService.desfazerEfeitoFinanceiro(t, usuarioId);
                t.setAtivo(false);
                transacaoRepository.save(t);
            }
        }

        return new ImportacaoDesfeitaDTO(importacaoId, transacoes.size());
    }

    // ─── Extração ────────────────────────────────────────────────────────────

    private List<TransacaoCandidataDTO> extrairCandidatas(MultipartFile arquivo,
                                                           FormatoDetectado formato,
                                                           Long usuarioId) {
        return switch (formato) {
            // Parser determinístico primeiro (grátis, sem cota); estrutura desconhecida → IA.
            case CSV -> csvExtratoParser.tentarParsear(arquivo)
                    .orElseGet(() -> extrairViaGemini(arquivo, "text/plain", PROMPT_EXTRATO, usuarioId));
            case PDF_FATURA -> extrairViaGemini(arquivo, "application/pdf", PROMPT_FATURA_PDF, usuarioId);
            case PDF_EXTRATO -> extrairViaGemini(arquivo, "application/pdf", PROMPT_EXTRATO, usuarioId);
        };
    }

    private List<TransacaoCandidataDTO> extrairViaGemini(MultipartFile arquivo, String mimeType,
                                                         String prompt, Long usuarioId) {
        byte[] bytes = lerBytes(arquivo);
        String resposta = geminiClient.extrairTexto(bytes, mimeType, prompt, usuarioId);
        return parsearRespostaGemini(resposta);
    }

    private List<TransacaoCandidataDTO> parsearRespostaGemini(String json) {
        try {
            var root = objectMapper.readTree(json);
            var transacoesNode = root.get("transacoes");
            if (transacoesNode == null || !transacoesNode.isArray()) {
                throw new RegraDeNegocioException(
                        "error.importacao.gemini_resposta_vazia",
                        "O modelo de IA retornou um formato inesperado.");
            }
            return objectMapper.convertValue(transacoesNode, new TypeReference<List<TransacaoCandidataDTO>>() {});
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // Nunca logar o JSON: contém descrições/valores das transações do usuário (dado sensível).
            log.error("Falha ao parsear resposta Gemini ({} chars): {}", json.length(), e.getMessage());
            throw new RegraDeNegocioException(
                    "error.importacao.gemini_resposta_vazia",
                    "Não foi possível interpretar a resposta da IA.");
        }
    }

    // ─── Deduplicação ────────────────────────────────────────────────────────

    private List<TransacaoCandidataDTO> marcarDuplicatas(List<TransacaoCandidataDTO> candidatas, Long usuarioId) {
        List<Map<String, Object>> payload = candidatas.stream()
                .map(c -> Map.<String, Object>of(
                        "indice", c.indice(), "data", c.data().toString(),
                        "valor", c.valor(), "descricao", c.descricao()))
                .toList();

        Set<Integer> duplicados;
        try {
            duplicados = Set.copyOf(transacaoRepository.buscarIndicesDuplicados(
                    usuarioId, objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar candidatas para deduplicação", e);
        }

        return candidatas.stream()
                .map(c -> duplicados.contains(c.indice()) ? c.comDuplicataDetectada() : c)
                .toList();
    }

    // ─── Enriquecimento de classificação ─────────────────────────────────────

    /**
     * Heurísticas pós-saneamento — apenas SUGEREM, nunca decidem (revisão humana obrigatória):
     * 1. Pix contendo o nome completo do usuário → TRANSFERENCIA_INTERNA + suspeita
     *    (desmarcada por padrão: evita importar receita real de homônimo como transferência).
     *    Exige nome com 2+ palavras — primeiro nome sozinho geraria falso positivo em massa.
     * 2. APORTE/RESGATE → sugere investimento por token do nome contido na descrição.
     */
    private List<TransacaoCandidataDTO> enriquecerClassificacao(List<TransacaoCandidataDTO> candidatas, Long usuarioId) {
        String nomeUsuario = TextoUtil.normalizar(usuarioService.buscarPorIdOuFalhar(usuarioId).getNome());
        boolean nomeElegivel = nomeUsuario.split("\\s+").length >= 2;

        boolean temInvestivel = candidatas.stream().anyMatch(c ->
                c.classificacao() == ClassificacaoCandidata.APORTE
                        || c.classificacao() == ClassificacaoCandidata.RESGATE);
        // Tokens pré-computados uma vez: evita re-normalizar cada investimento para cada candidata.
        List<TokensInvestimento> investimentos = temInvestivel
                ? investimentoRepository.findByUsuarioId(usuarioId).stream()
                        .map(inv -> new TokensInvestimento(inv.getId(),
                                TextoUtil.normalizar(inv.getDescricao()).split("\\s+")))
                        .toList()
                : List.of();

        return candidatas.stream().map(c -> {
            String descricao = TextoUtil.normalizar(c.descricao());

            if (c.classificacao() == ClassificacaoCandidata.NORMAL
                    && nomeElegivel
                    && descricao.contains("pix")
                    && descricao.contains(nomeUsuario)) {
                return c.comClassificacao(ClassificacaoCandidata.TRANSFERENCIA_INTERNA, null, true);
            }

            if (c.classificacao() == ClassificacaoCandidata.APORTE
                    || c.classificacao() == ClassificacaoCandidata.RESGATE) {
                Long sugerido = sugerirInvestimento(descricao, investimentos);
                if (sugerido != null) {
                    return c.comClassificacao(c.classificacao(), sugerido, c.suspeita());
                }
            }
            return c;
        }).toList();
    }

    private record TokensInvestimento(Long id, String[] tokens) {}

    /** Primeiro investimento com algum token significativo (4+ chars) do nome contido na descrição. */
    private Long sugerirInvestimento(String descricaoNormalizada, List<TokensInvestimento> investimentos) {
        for (TokensInvestimento inv : investimentos) {
            for (String token : inv.tokens()) {
                if (token.length() >= 4 && descricaoNormalizada.contains(token)) {
                    return inv.id();
                }
            }
        }
        return null;
    }

    // ─── Confirmação ─────────────────────────────────────────────────────────

    /** anoPresumido > ano corrente furaria a validação de dataMaxima do saneamento (que roda só no upload). */
    private void validarAnoPresumido(Integer anoPresumido) {
        if (anoPresumido != null && anoPresumido > LocalDate.now().getYear()) {
            throw new RegraDeNegocioException(
                    "error.importacao.ano_invalido",
                    "O ano informado não pode ser no futuro.");
        }
    }

    private TransacaoCandidataDTO aplicarAnoPresumido(TransacaoCandidataDTO c, Integer anoPresumido) {
        if (!c.dataPresumida() || anoPresumido == null || c.data() == null) return c;
        return c.comDataDefinitiva(c.data().withYear(anoPresumido));
    }

    /**
     * Pré-cria faturas históricas para períodos passados antes do lote de transações.
     * Evita que consumirLimite bloqueie importações de dados antigos.
     * O mês é o de REFERÊNCIA da fatura (compra no dia do fechamento ou depois pertence ao mês
     * seguinte) — usar o mês civil criaria uma fatura vazia e deixaria a transação cair em
     * outra, comum e ABERTA, gerando dívida fantasma que consome limite.
     */
    private void precriarFaturasHistoricas(Long cartaoId, List<TransacaoCandidataDTO> selecionadas, Long usuarioId) {
        YearMonth atual = YearMonth.now();
        CartaoEntity cartao = cartaoService.buscarCartaoValidado(cartaoId, usuarioId);

        selecionadas.stream()
                .filter(c -> c.tipo() == TipoTransacao.DESPESA && c.data() != null)
                .map(c -> FaturaDateUtil.mesReferencia(c.data(), cartao.getDiaFechamento()))
                .filter(ym -> ym.isBefore(atual))
                .collect(Collectors.toSet())
                .forEach(ym -> faturaService.criarFaturaHistorica(cartao, ym.getMonthValue(), ym.getYear()));
    }

    // ─── Auxiliares ──────────────────────────────────────────────────────────

    private ImportacaoEntity carregarSessaoValidada(UUID importacaoId, Long usuarioId) {
        ImportacaoEntity sessao = importacaoRepository.findById(importacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão de importação não encontrada."));
        ValidacaoRecursoUtil.validarPropriedade(sessao.getUsuario().getId(), usuarioId, "importação");
        if (sessao.getStatus() == StatusImportacao.CANCELADA) {
            throw new RecursoNaoEncontradoException("Sessão de importação não encontrada.");
        }
        return sessao;
    }

    /**
     * Saneia a saída de fontes não-confiáveis (Gemini/CSV) antes de persistir na sessão:
     * re-indexa sequencialmente, valida valor/data/parcelas e trunca descrições.
     * Defesa contra prompt injection embutido no documento e payloads abusivos —
     * a resposta do modelo é tratada como input externo, nunca como dado confiável.
     */
    private List<TransacaoCandidataDTO> sanearCandidatas(List<TransacaoCandidataDTO> brutas) {
        if (brutas.size() > ImportacaoConstantes.MAX_CANDIDATAS) {
            throw new RegraDeNegocioException(
                    "error.importacao.limite_candidatas",
                    "O documento excede o limite de " + ImportacaoConstantes.MAX_CANDIDATAS + " transações por importação.");
        }

        LocalDate dataMaxima = LocalDate.now().plusYears(1);
        List<TransacaoCandidataDTO> saneadas = new ArrayList<>();
        int indice = 0;
        for (TransacaoCandidataDTO c : brutas) {
            boolean invalida = c.descricao() == null || c.descricao().isBlank()
                    || c.tipo() == null || c.data() == null
                    || c.valor() == null
                    || c.valor().compareTo(BigDecimal.ZERO) <= 0
                    || c.valor().compareTo(VALOR_MAXIMO) > 0
                    || c.data().isBefore(DATA_MINIMA)
                    || c.data().isAfter(dataMaxima);
            if (invalida) {
                log.warn("Candidata descartada no saneamento: indice={}, motivo=campos inválidos", c.indice());
                continue;
            }

            String descricao = c.descricao().strip();
            if (descricao.length() > MAX_DESCRICAO) {
                descricao = descricao.substring(0, MAX_DESCRICAO);
            }

            boolean parcelasValidas = c.numeroParcela() != null && c.totalParcelas() != null
                    && c.numeroParcela() >= 1 && c.totalParcelas() >= 2
                    && c.totalParcelas() <= 72 && c.numeroParcela() <= c.totalParcelas();

            saneadas.add(new TransacaoCandidataDTO(
                    indice++, descricao,
                    c.valor().setScale(2, RoundingMode.HALF_UP),
                    c.tipo(), c.data(), c.dataPresumida(), c.metodoPagamento(),
                    parcelasValidas ? c.numeroParcela() : null,
                    parcelasValidas ? c.totalParcelas() : null,
                    c.suspeita(), false,
                    c.classificacao(), null));
        }

        if (saneadas.isEmpty()) {
            throw new RegraDeNegocioException(
                    "error.importacao.nenhuma_transacao",
                    "Nenhuma transação válida encontrada no documento.");
        }
        if (saneadas.size() < brutas.size()) {
            log.info("Saneamento de candidatas: {} de {} válidas", saneadas.size(), brutas.size());
        }
        return saneadas;
    }

    private String serializarCandidatas(List<TransacaoCandidataDTO> candidatas) {
        try {
            return objectMapper.writeValueAsString(candidatas);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar candidatas", e);
        }
    }

    private List<TransacaoCandidataDTO> deserializarCandidatas(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao deserializar candidatas", e);
        }
    }

    private byte[] lerBytes(MultipartFile arquivo) {
        try {
            return arquivo.getBytes();
        } catch (Exception e) {
            throw new RegraDeNegocioException(
                    "error.importacao.arquivo_invalido",
                    "Não foi possível ler o arquivo.");
        }
    }

    // ─── DTOs de resposta locais ──────────────────────────────────────────────

    public record ImportacaoConfirmadaDTO(UUID importacaoId, int criadas, int erros) {}
    public record ImportacaoDesfeitaDTO(UUID importacaoId, int excluidas) {}
}
