package org.app_financeiro.backend.service;

import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import lombok.RequiredArgsConstructor;
import org.app_financeiro.backend.dto.request.RelatorioFiltroDTO;
import org.app_financeiro.backend.dto.response.RelatorioCsvLinhaDTO;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.TipoFiltroRelatorio;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.enums.TipoTransacao;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.math.BigDecimal;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class RelatorioService {

    private static final Logger log = LoggerFactory.getLogger(RelatorioService.class);

    private final TransacaoRepository transacaoRepository;
    private final TemplateEngine templateEngine;

    public byte[] exportarRelatorio(RelatorioFiltroDTO filtro, Long usuarioId, String formatoDesejado) {

        TipoTransacao tipoMapeado = filtro.tipoFiltro() == TipoFiltroRelatorio.GERAL ? null : (
                filtro.tipoFiltro() == TipoFiltroRelatorio.RECEITA ?
                        TipoTransacao.RECEITA : TipoTransacao.DESPESA);
        
        List<TransacaoEntity> transacoes = transacaoRepository.buscarParaRelatorio(
                usuarioId, 
                filtro.dataInicio(), 
                filtro.dataFim(),
                tipoMapeado,
                filtro.statusTransacao()
        );

        NumberFormat formatadorMoeda = NumberFormat.getInstance(Locale.of("pt", "BR"));
        formatadorMoeda.setMinimumFractionDigits(2);
        formatadorMoeda.setMaximumFractionDigits(2);

        List<RelatorioCsvLinhaDTO> transacoesProntasParaEmissao = transacoes.stream().map(transacaoBd -> {
            return RelatorioCsvLinhaDTO.builder()
                    .data(transacaoBd.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .tipo(transacaoBd.isTransferencia() ? "TRANSFERENCIA" : transacaoBd.getTipo().name())
                    .descricao(transacaoBd.getDescricao())
                    .descricaoCategoria(transacaoBd.getCategoria() != null ? transacaoBd.getCategoria().getNome() : "Sem Categoria")
                    .contaOuCartao(transacaoBd.getConta() != null ? transacaoBd.getConta().getNome() : (transacaoBd.getCartao() != null ? transacaoBd.getCartao().getNome() : ""))
                    .valor(formatadorMoeda.format(transacaoBd.getValor()))
                    .status(transacaoBd.getStatus() != null ? transacaoBd.getStatus().name() : "PENDENTE")
                    .build();
        }).toList();

        BigDecimal totalReceitas = transacoes.stream()
            .filter(tx -> !tx.isTransferencia())
                .filter(tx -> tx.getTipo() == TipoTransacao.RECEITA)
                .map(TransacaoEntity::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDespesas = transacoes.stream()
            .filter(tx -> !tx.isTransferencia())
                .filter(tx -> tx.getTipo() == TipoTransacao.DESPESA)
                .map(TransacaoEntity::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (formatoDesejado.equalsIgnoreCase("PDF")) {
            return gerarBytesPdf(transacoesProntasParaEmissao, filtro, formatadorMoeda.format(totalReceitas), formatadorMoeda.format(totalDespesas));
        } else {
            return gerarBytesCsv(transacoesProntasParaEmissao, filtro);
        }
    }


    private byte[] gerarBytesPdf(List<RelatorioCsvLinhaDTO> transacoes, RelatorioFiltroDTO filtro, String totalReceitas, String totalDespesas) {

        Context context = new Context();
        context.setVariable("transacoes", transacoes);
        context.setVariable("tituloRelatorio", "Extrato " + filtro.tipoFiltro().name());
        context.setVariable("periodoGeracao", "Período: " + filtro.dataInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " a " + filtro.dataFim().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        context.setVariable("mostrarResumo", true);
        context.setVariable("totalReceitas", "R$ " + totalReceitas);
        context.setVariable("totalDespesas", "R$ " + totalDespesas);

        String html = templateEngine.process("relatorios/extrato-base", context);

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            
            String baseUri = Objects.requireNonNull(getClass().getResource("/")).toExternalForm();

            builder.withHtmlContent(html, baseUri)
                   .toStream(stream)
                   .run();

            return stream.toByteArray();
        } catch (Exception e) {
            log.error("Falha ao renderizar relatório PDF", e);
            throw new RegraDeNegocioException("error.relatorio.render_falhou", "Não foi possível gerar o relatório. Tente novamente.");
        }
    }

    private byte[] gerarBytesCsv(List<RelatorioCsvLinhaDTO> transacoes, RelatorioFiltroDTO filtro) {

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {

            output.write(new byte[] { (byte)0xEF, (byte)0xBB, (byte)0xBF });

            StatefulBeanToCsv<RelatorioCsvLinhaDTO> motorCsv = new StatefulBeanToCsvBuilder<RelatorioCsvLinhaDTO>(writer)
                    .withSeparator(';')
                    .build();

            motorCsv.write(transacoes);
            writer.flush();
            
            return output.toByteArray();
        } catch (Exception e) {
            log.error("Falha ao renderizar relatório CSV", e);
            throw new RegraDeNegocioException("error.relatorio.render_falhou", "Não foi possível gerar o relatório. Tente novamente.");
        }
    }

}
