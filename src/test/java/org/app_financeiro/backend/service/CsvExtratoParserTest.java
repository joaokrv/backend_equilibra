package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.importacao.TransacaoCandidataDTO;
import org.app_financeiro.backend.enums.ClassificacaoCandidata;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parser genérico de extratos CSV — detecção dinâmica de estrutura, formatos de
 * data/valor de bancos diversos, e fallback (Optional.empty) quando irreconhecível.
 */
class CsvExtratoParserTest {

    private CsvExtratoParser parser;

    @BeforeEach
    void setUp() {
        parser = new CsvExtratoParser();
    }

    private MockMultipartFile arquivo(String conteudo) {
        return new MockMultipartFile("arquivo", "extrato.csv", "text/csv",
                conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private List<TransacaoCandidataDTO> parsear(String conteudo) {
        Optional<List<TransacaoCandidataDTO>> resultado = parser.tentarParsear(arquivo(conteudo));
        assertThat(resultado).isPresent();
        return resultado.get();
    }

    // ─── Formato Sicredi (compatibilidade com o comportamento anterior) ────────

    private static final String SICREDI = """
            Extrato Conta Corrente
            ;;;
            Associado: FULANO DE TAL
            Cooperativa: 0000
            Conta: 00000-0
            Data Lançamento;Histórico;Descrição;Valor;Saldo
            """;

    @Test
    @DisplayName("Sicredi: preamble de 5 linhas + header ; — despesa com valor pt-BR negativo")
    void deveParsearFormatoSicredi() {
        List<TransacaoCandidataDTO> candidatas = parsear(
                SICREDI + "01/03/2026;;Compra Supermercado;-150,50;1000,00");

        TransacaoCandidataDTO c = candidatas.get(0);
        assertThat(c.tipo()).isEqualTo(TipoTransacao.DESPESA);
        assertThat(c.valor()).isEqualByComparingTo(new BigDecimal("150.50"));
        assertThat(c.data()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(c.descricao()).isEqualTo("Compra Supermercado");
        assertThat(c.metodoPagamento()).isEqualTo(MetodoPagamento.PIX);
    }

    @Test
    @DisplayName("Método de pagamento detectado por palavra-chave: boleto, TED/DOC, débito, saque; PIX é o padrão")
    void deveDetectarMetodoPorPalavraChave() {
        List<TransacaoCandidataDTO> candidatas = parsear(SICREDI
                + "01/03/2026;;Pagamento Boleto Energia;-100,00;900,00\n"
                + "02/03/2026;TED Recebida;;200,00;1100,00\n"
                + "03/03/2026;;Compra no Debito Mercado;-50,00;1050,00\n"
                + "04/03/2026;;Saque Dinheiro Caixa;-80,00;970,00\n"
                + "05/03/2026;;Compra Qualquer Loja;-30,00;940,00");

        assertThat(candidatas.get(0).metodoPagamento()).isEqualTo(MetodoPagamento.BOLETO);
        assertThat(candidatas.get(1).metodoPagamento()).isEqualTo(MetodoPagamento.TRANSFERENCIA);
        assertThat(candidatas.get(2).metodoPagamento()).isEqualTo(MetodoPagamento.CARTAO_DEBITO);
        assertThat(candidatas.get(3).metodoPagamento()).isEqualTo(MetodoPagamento.DINHEIRO);
        assertThat(candidatas.get(4).metodoPagamento()).isEqualTo(MetodoPagamento.PIX);
    }

    @Test
    @DisplayName("Sicredi: histórico concatenado à descrição e classificação APORTE/RESGATE")
    void deveConcatenarHistoricoEClassificar() {
        List<TransacaoCandidataDTO> candidatas = parsear(SICREDI
                + "01/03/2026;Aplicação;Cdb Banco Inter;-500,00;500,00\n"
                + "02/03/2026;Resgate;Cdb Banco Inter;500,00;1000,00");

        assertThat(candidatas.get(0).descricao()).isEqualTo("Aplicação Cdb Banco Inter");
        assertThat(candidatas.get(0).classificacao()).isEqualTo(ClassificacaoCandidata.APORTE);
        assertThat(candidatas.get(0).suspeita()).isTrue();
        assertThat(candidatas.get(1).classificacao()).isEqualTo(ClassificacaoCandidata.RESGATE);
    }

    @Test
    @DisplayName("'Resgate Aplicacao Financeira' classifica como RESGATE (resgate vence aplicação)")
    void resgateVenceAplicacaoNaClassificacao() {
        List<TransacaoCandidataDTO> candidatas = parsear(SICREDI
                + "01/03/2026;Resgate Aplicacao Financeira;Cdb;500,00;1000,00");

        assertThat(candidatas.get(0).classificacao()).isEqualTo(ClassificacaoCandidata.RESGATE);
    }

    @Test
    @DisplayName("Pagamento de fatura permanece NORMAL com suspeita=true")
    void pagamentoDeFaturaEhNormalESuspeito() {
        List<TransacaoCandidataDTO> candidatas = parsear(SICREDI
                + "01/03/2026;;PAGAMENTO DE FATURA CARTAO;-300,00;700,00");

        assertThat(candidatas.get(0).classificacao()).isEqualTo(ClassificacaoCandidata.NORMAL);
        assertThat(candidatas.get(0).suspeita()).isTrue();
    }

    // ─── Outros bancos ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Estilo Nubank conta: header na 1ª linha, vírgula, decimal US e descrição entre aspas")
    void deveParsearFormatoComVirgulaEDecimalUs() {
        List<TransacaoCandidataDTO> candidatas = parsear("""
                Data,Valor,Identificador,Descrição
                01/03/2026,-150.50,abc-123,"Transferência enviada, Fulano"
                02/03/2026,1328.30,def-456,Pix recebido
                """);

        assertThat(candidatas).hasSize(2);
        assertThat(candidatas.get(0).valor()).isEqualByComparingTo(new BigDecimal("150.50"));
        assertThat(candidatas.get(0).descricao()).isEqualTo("Transferência enviada, Fulano");
        assertThat(candidatas.get(1).tipo()).isEqualTo(TipoTransacao.RECEITA);
        assertThat(candidatas.get(1).valor()).isEqualByComparingTo(new BigDecimal("1328.30"));
    }

    @Test
    @DisplayName("Estilo cartão Nubank: colunas date/title/amount em inglês e data ISO")
    void deveParsearHeaderEmInglesComDataIso() {
        List<TransacaoCandidataDTO> candidatas = parsear("""
                date,category,title,amount
                2026-03-01,supermercado,Mercado Pao De Acucar,150.50
                """);

        assertThat(candidatas.get(0).data()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(candidatas.get(0).descricao()).isEqualTo("Mercado Pao De Acucar");
        assertThat(candidatas.get(0).valor()).isEqualByComparingTo(new BigDecimal("150.50"));
    }

    @Test
    @DisplayName("Delimitador tab e valor com R$ e milhar pt-BR")
    void deveParsearDelimitadorTabEValorComSimbolo() {
        List<TransacaoCandidataDTO> candidatas = parsear(
                "Data\tLançamento\tValor\n" +
                "15/02/2026\tPix Mercado\tR$ -1.328,30\n");

        assertThat(candidatas.get(0).valor()).isEqualByComparingTo(new BigDecimal("1328.30"));
        assertThat(candidatas.get(0).tipo()).isEqualTo(TipoTransacao.DESPESA);
    }

    @Test
    @DisplayName("Sufixo D/C define o sinal (padrão de bancos tradicionais)")
    void deveInterpretarSufixoDebitoCredito() {
        List<TransacaoCandidataDTO> candidatas = parsear("""
                Data;Descrição;Valor
                01/03/2026;Compra Débito;150,00D
                02/03/2026;Depósito;200,00C
                """);

        assertThat(candidatas.get(0).tipo()).isEqualTo(TipoTransacao.DESPESA);
        assertThat(candidatas.get(1).tipo()).isEqualTo(TipoTransacao.RECEITA);
    }

    @Test
    @DisplayName("Separador único com 3 dígitos depois é milhar: '1.328' → 1328, '1,328' → 1328")
    void deveInterpretarSeparadorUnicoComTresDigitosComoMilhar() {
        List<TransacaoCandidataDTO> candidatas = parsear("""
                Data;Descrição;Valor
                01/03/2026;Aporte Grande;-1.328
                02/03/2026;Deposito;1,328
                03/03/2026;Salario;12.345.678
                """);

        assertThat(candidatas.get(0).valor()).isEqualByComparingTo(new BigDecimal("1328"));
        assertThat(candidatas.get(1).valor()).isEqualByComparingTo(new BigDecimal("1328"));
        assertThat(candidatas.get(2).valor()).isEqualByComparingTo(new BigDecimal("12345678"));
    }

    @Test
    @DisplayName("Separador único com 1 ou 2 dígitos depois permanece decimal: '150.50', '1,5'")
    void deveManterSeparadorUnicoComPoucosDigitosComoDecimal() {
        List<TransacaoCandidataDTO> candidatas = parsear("""
                Data;Descrição;Valor
                01/03/2026;Compra;-150.50
                02/03/2026;Tarifa;-1,5
                """);

        assertThat(candidatas.get(0).valor()).isEqualByComparingTo(new BigDecimal("150.50"));
        assertThat(candidatas.get(1).valor()).isEqualByComparingTo(new BigDecimal("1.5"));
    }

    @Test
    @DisplayName("Valor entre parênteses é negativo (convenção contábil)")
    void deveInterpretarParentesesComoNegativo() {
        List<TransacaoCandidataDTO> candidatas = parsear("""
                Data;Descrição;Valor
                01/03/2026;Tarifa;(50,00)
                """);

        assertThat(candidatas.get(0).tipo()).isEqualTo(TipoTransacao.DESPESA);
        assertThat(candidatas.get(0).valor()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ─── Robustez ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Linhas de rodapé/totalizador (sem data válida) são ignoradas sem erro")
    void deveIgnorarRodapeSemDataValida() {
        List<TransacaoCandidataDTO> candidatas = parsear("""
                Data;Descrição;Valor
                01/03/2026;Compra;-10,00
                Saldo final;;990,00
                """);

        assertThat(candidatas).hasSize(1);
    }

    @Test
    @DisplayName("Linhas em branco são ignoradas e índices permanecem sequenciais")
    void deveIgnorarLinhasEmBranco() {
        List<TransacaoCandidataDTO> candidatas = parsear("""
                Data;Descrição;Valor
                01/03/2026;Compra 1;-10,00

                02/03/2026;Compra 2;-20,00
                """);

        assertThat(candidatas).hasSize(2);
        assertThat(candidatas.get(0).indice()).isZero();
        assertThat(candidatas.get(1).indice()).isEqualTo(1);
    }

    @Test
    @DisplayName("Valor ilegível em linha com data válida → erro 400 com o número da linha")
    void deveRejeitarValorIlegivelComNumeroDaLinha() {
        assertThatThrownBy(() -> parser.tentarParsear(arquivo("""
                Data;Descrição;Valor
                01/03/2026;Compra;abc
                """)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("linha 2");
    }

    @Test
    @DisplayName("ANTI-ABUSO: documento acima de 500 transações é rejeitado")
    void deveRejeitarAcimaDoLimiteDeCandidatas() {
        String linhas = IntStream.rangeClosed(1, 501)
                .mapToObj(i -> "01/03/2026;Compra " + i + ";-10,00")
                .collect(Collectors.joining("\n"));

        assertThatThrownBy(() -> parser.tentarParsear(arquivo("Data;Descrição;Valor\n" + linhas)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("500");
    }

    // ─── Fallback ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Estrutura não reconhecida (sem cabeçalho identificável) → Optional.empty para fallback IA")
    void deveDelegarAoFallbackQuandoEstruturaNaoReconhecida() {
        Optional<List<TransacaoCandidataDTO>> resultado = parser.tentarParsear(arquivo("""
                relatorio interno do banco
                01/03/2026|Compra|-10,00
                02/03/2026|Pix|20,00
                """));

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Cabeçalho reconhecido mas nenhuma linha de dados parseável → Optional.empty")
    void deveDelegarAoFallbackQuandoNaoHaDados() {
        Optional<List<TransacaoCandidataDTO>> resultado = parser.tentarParsear(arquivo("""
                Data;Descrição;Valor
                total;;-999,00
                """));

        assertThat(resultado).isEmpty();
    }
}
