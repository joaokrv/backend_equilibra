package org.app_financeiro.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.app_financeiro.backend.dto.importacao.TransacaoCandidataDTO;
import org.app_financeiro.backend.enums.ClassificacaoCandidata;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.util.ImportacaoConstantes;
import org.app_financeiro.backend.util.TextoUtil;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parser determinístico de extratos CSV de QUALQUER banco.
 *
 * Em vez de assumir um layout fixo, detecta a estrutura dinamicamente:
 * - delimitador (";", "," ou tab), com suporte a campos entre aspas;
 * - linha de cabeçalho, localizada pelo nome das colunas (data/valor/descrição/histórico),
 *   em qualquer posição dentro das primeiras linhas (preamble de tamanho variável);
 * - datas em dd/MM/yyyy, dd/MM/yy, yyyy-MM-dd, dd-MM-yyyy ou dd.MM.yyyy;
 * - valores em pt-BR ("1.328,30"), US ("1,328.30"), com "R$", parênteses ou sufixo D/C.
 *
 * Estrutura não reconhecida → {@link Optional#empty()}: o chamador decide o fallback
 * (extração via IA). Linhas de rodapé/totalizador sem data válida são ignoradas;
 * valor ilegível em linha com data válida é erro do arquivo (400 com número da linha).
 */
@Slf4j
@Component
public class CsvExtratoParser {

    private static final int MAX_LINHAS_BUSCA_CABECALHO = 30;
    private static final char[] DELIMITADORES = {';', ',', '\t'};
    private static final List<DateTimeFormatter> FORMATOS_DATA = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    private static final Pattern PAGAMENTO_FATURA = Pattern.compile("pagamento.*fatura", Pattern.CASE_INSENSITIVE);

    private record Estrutura(int linhaCabecalho, char delimitador,
                             int idxData, int idxValor, int idxDescricao, int idxHistorico) {}

    /** Vazio = estrutura não reconhecida (não é falha do arquivo — chamador aciona o fallback). */
    public Optional<List<TransacaoCandidataDTO>> tentarParsear(MultipartFile arquivo) {
        List<String> linhas = lerLinhas(arquivo);
        Estrutura estrutura = detectarEstrutura(linhas);
        if (estrutura == null) {
            log.info("Estrutura de CSV não reconhecida ({} linhas) — delegando ao fallback", linhas.size());
            return Optional.empty();
        }

        List<TransacaoCandidataDTO> candidatas = new ArrayList<>();
        int maiorIndice = Math.max(estrutura.idxData(),
                Math.max(estrutura.idxValor(), Math.max(estrutura.idxDescricao(), estrutura.idxHistorico())));
        for (int i = estrutura.linhaCabecalho() + 1; i < linhas.size(); i++) {
            String linha = linhas.get(i).trim();
            if (linha.isBlank()) continue;

            List<String> colunas = dividir(linha, estrutura.delimitador());
            if (colunas.size() <= maiorIndice) continue;

            LocalDate data = parsearData(colunas.get(estrutura.idxData()));
            if (data == null) continue; // rodapé, totalizador ou linha institucional

            if (candidatas.size() >= ImportacaoConstantes.MAX_CANDIDATAS) {
                throw new RegraDeNegocioException(
                        "error.importacao.limite_candidatas",
                        "O documento excede o limite de " + ImportacaoConstantes.MAX_CANDIDATAS + " transações por importação.");
            }
            candidatas.add(montarCandidata(colunas, estrutura, data, candidatas.size(), i));
        }

        if (candidatas.isEmpty()) {
            log.info("Cabeçalho reconhecido mas nenhuma linha de dados parseável — delegando ao fallback");
            return Optional.empty();
        }
        return Optional.of(candidatas);
    }

    private TransacaoCandidataDTO montarCandidata(List<String> colunas, Estrutura e,
                                                  LocalDate data, int indice, int numeroLinha) {
        BigDecimal valor = parsearValor(colunas.get(e.idxValor()), numeroLinha);
        String historico = e.idxHistorico() >= 0 ? colunas.get(e.idxHistorico()) : "";
        String descricao = e.idxDescricao() >= 0 ? colunas.get(e.idxDescricao()) : "";

        boolean negativo = valor.compareTo(BigDecimal.ZERO) < 0;
        String descricaoFinal = (historico.isBlank() ? descricao : historico + " " + descricao).strip();
        String base = TextoUtil.normalizar(descricaoFinal);

        ClassificacaoCandidata classificacao = classificar(base);
        boolean suspeita = classificacao != ClassificacaoCandidata.NORMAL
                || PAGAMENTO_FATURA.matcher(base).find();

        return new TransacaoCandidataDTO(
                indice,
                descricaoFinal,
                valor.abs(),
                negativo ? TipoTransacao.DESPESA : TipoTransacao.RECEITA,
                data,
                false,
                detectarMetodo(base),
                null,
                null,
                suspeita,
                false,
                classificacao,
                null);
    }

    /**
     * Pagamento de fatura permanece NORMAL (+suspeita via regex): a cobertura correta
     * dele é a importação da própria fatura PDF, nunca aporte/transferência.
     * "resgate" checado antes de "aplicac": "Resgate Aplicacao Financeira" é resgate.
     */
    private ClassificacaoCandidata classificar(String descricaoNormalizada) {
        if (descricaoNormalizada.contains("resgat")) return ClassificacaoCandidata.RESGATE;
        if (descricaoNormalizada.contains("aplicac")) return ClassificacaoCandidata.APORTE;
        return ClassificacaoCandidata.NORMAL;
    }

    /**
     * Método sugerido pela palavra-chave do histórico/descrição — apenas categorização.
     * Não decide se o saldo move: extrato de conta sempre é PAGO (dinheiro já movimentado);
     * o ImportacaoItemProcessor força isso independentemente do método. PIX é o padrão.
     */
    private MetodoPagamento detectarMetodo(String descricaoNormalizada) {
        if (descricaoNormalizada.contains("boleto")) return MetodoPagamento.BOLETO;
        if (descricaoNormalizada.contains("ted") || descricaoNormalizada.contains("doc")
                || descricaoNormalizada.contains("transferencia")) return MetodoPagamento.TRANSFERENCIA;
        if (descricaoNormalizada.contains("debito")) return MetodoPagamento.CARTAO_DEBITO;
        if (descricaoNormalizada.contains("dinheiro") || descricaoNormalizada.contains("saque")) return MetodoPagamento.DINHEIRO;
        return MetodoPagamento.PIX;
    }

    // ─── Detecção de estrutura ────────────────────────────────────────────────

    private Estrutura detectarEstrutura(List<String> linhas) {
        int limite = Math.min(MAX_LINHAS_BUSCA_CABECALHO, linhas.size());
        for (int i = 0; i < limite; i++) {
            for (char delimitador : DELIMITADORES) {
                Estrutura estrutura = tentarMapearCabecalho(linhas.get(i), i, delimitador);
                if (estrutura != null) return estrutura;
            }
        }
        return null;
    }

    private Estrutura tentarMapearCabecalho(String linha, int numeroLinha, char delimitador) {
        List<String> colunas = dividir(linha, delimitador);
        if (colunas.size() < 3) return null;

        int idxData = -1, idxValor = -1, idxDescricao = -1, idxHistorico = -1;
        for (int c = 0; c < colunas.size(); c++) {
            String celula = TextoUtil.normalizar(colunas.get(c));
            if (celula.isBlank()) continue;
            if (idxData < 0 && (celula.contains("data") || celula.contains("date"))) {
                idxData = c;
            } else if (idxValor < 0 && (celula.contains("valor") || celula.contains("amount")
                    || celula.contains("value") || celula.contains("montante"))) {
                idxValor = c;
            } else if (idxHistorico < 0 && celula.contains("histor")) {
                idxHistorico = c;
            } else if (idxDescricao < 0 && (celula.contains("descri") || celula.contains("lancamento")
                    || celula.contains("memo") || celula.contains("detalhe")
                    || celula.contains("transa") || celula.contains("title"))) {
                idxDescricao = c;
            }
        }

        if (idxData < 0 || idxValor < 0 || (idxDescricao < 0 && idxHistorico < 0)) return null;
        if (idxDescricao < 0) {
            idxDescricao = idxHistorico;
            idxHistorico = -1;
        }
        return new Estrutura(numeroLinha, delimitador, idxData, idxValor, idxDescricao, idxHistorico);
    }

    // ─── Parsing de célula ────────────────────────────────────────────────────

    /** Split ciente de aspas: vírgula dentro de "..." não separa colunas. */
    private List<String> dividir(String linha, char delimitador) {
        List<String> colunas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean dentroDeAspas = false;
        for (char ch : linha.toCharArray()) {
            if (ch == '"') {
                dentroDeAspas = !dentroDeAspas;
            } else if (ch == delimitador && !dentroDeAspas) {
                colunas.add(atual.toString().trim());
                atual.setLength(0);
            } else {
                atual.append(ch);
            }
        }
        colunas.add(atual.toString().trim());
        return colunas;
    }

    private LocalDate parsearData(String raw) {
        String texto = raw.strip();
        if (texto.isBlank()) return null;
        for (DateTimeFormatter formato : FORMATOS_DATA) {
            try {
                return LocalDate.parse(texto, formato);
            } catch (DateTimeParseException ignorada) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    /**
     * "1.328,30" → 1328.30 | "1,328.30" → 1328.30 | "R$ -100,00" → -100.00
     * "(50,00)" → -50.00 | "150,00D" → -150.00 | "150,00C" → 150.00
     */
    private BigDecimal parsearValor(String raw, int numeroLinha) {
        String texto = raw.replace("R$", "").replace("$", "")
                .replace(" ", "").replace(" ", "").strip();

        boolean negativo = false;
        if (texto.startsWith("(") && texto.endsWith(")")) {
            negativo = true;
            texto = texto.substring(1, texto.length() - 1);
        }
        char ultimo = texto.isEmpty() ? ' ' : Character.toUpperCase(texto.charAt(texto.length() - 1));
        if (ultimo == 'D' || ultimo == 'C') {
            negativo = negativo || ultimo == 'D';
            texto = texto.substring(0, texto.length() - 1);
        }
        if (texto.startsWith("-")) {
            negativo = true;
            texto = texto.substring(1);
        }

        boolean temPonto = texto.contains(".");
        boolean temVirgula = texto.contains(",");
        String normalizado;
        if (temPonto && temVirgula) {
            normalizado = texto.lastIndexOf(',') > texto.lastIndexOf('.')
                    ? texto.replace(".", "").replace(",", ".")   // pt-BR: 1.328,30
                    : texto.replace(",", "");                     // US: 1,328.30
        } else if (temVirgula) {
            normalizado = resolverSeparadorUnico(texto, ',');
        } else if (temPonto) {
            normalizado = resolverSeparadorUnico(texto, '.');
        } else {
            normalizado = texto;
        }

        try {
            BigDecimal valor = new BigDecimal(normalizado);
            return negativo ? valor.negate() : valor;
        } catch (NumberFormatException e) {
            throw new RegraDeNegocioException(
                    "error.importacao.arquivo_invalido",
                    "Valor inválido no CSV na linha " + (numeroLinha + 1) + ": " + raw);
        }
    }

    /**
     * Separador único é decimal ("150.50", "1,5"), EXCETO no padrão de milhar — todos os
     * grupos após o primeiro com exatamente 3 dígitos ("1.328" → 1328, "1.234.567" → 1234567).
     * Valores monetários em extrato usam 2 casas decimais; 3 dígitos após o separador é milhar.
     */
    private String resolverSeparadorUnico(String texto, char separador) {
        String[] grupos = texto.split(Pattern.quote(String.valueOf(separador)), -1);
        boolean padraoMilhar = grupos.length >= 2
                && !grupos[0].isEmpty()
                && java.util.Arrays.stream(grupos).skip(1)
                        .allMatch(g -> g.length() == 3 && g.chars().allMatch(Character::isDigit));
        if (padraoMilhar) {
            return texto.replace(String.valueOf(separador), "");
        }
        return separador == ',' ? texto.replace(',', '.') : texto;
    }

    private List<String> lerLinhas(MultipartFile arquivo) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new RegraDeNegocioException(
                    "error.importacao.arquivo_invalido",
                    "Não foi possível ler o arquivo CSV.");
        }
    }
}
