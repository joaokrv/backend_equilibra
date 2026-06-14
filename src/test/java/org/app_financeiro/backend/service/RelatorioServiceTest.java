package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.RelatorioFiltroDTO;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoFiltroRelatorio;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regressão de segurança: o CSV exportado precisa neutralizar formula injection
 * (células iniciadas por = + - @ \t \r) prefixando com aspa simples, conforme
 * exigido pelo CLAUDE.md. Trava o comportamento de RelatorioService.neutralizarFormulaCsv.
 */
@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private RelatorioService relatorioService;

    private TransacaoEntity transacaoComDescricao(String descricao) {
        TransacaoEntity tx = new TransacaoEntity();
        tx.setDescricao(descricao);
        tx.setValor(new BigDecimal("10.00"));
        tx.setData(LocalDate.of(2026, 1, 15));
        tx.setTipo(TipoTransacao.DESPESA);
        tx.setStatus(StatusTransacao.PAGO);
        tx.setTransferencia(false);
        return tx;
    }

    private String exportarCsv(String descricao) {
        when(transacaoRepository.buscarParaRelatorio(any(), any(), any(), any(), any()))
                .thenReturn(List.of(transacaoComDescricao(descricao)));

        RelatorioFiltroDTO filtro = new RelatorioFiltroDTO(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                TipoFiltroRelatorio.GERAL, null);

        byte[] bytes = relatorioService.exportarRelatorio(filtro, 1L, "CSV");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void deveNeutralizarFormulaIniciadaComIgual() {
        String csv = exportarCsv("=SUM(1+1)");
        assertThat(csv).contains("'=SUM(1+1)");
    }

    @Test
    void deveNeutralizarFormulaIniciadaComArroba() {
        String csv = exportarCsv("@HYPERLINK(\"http://malicioso\")");
        assertThat(csv).contains("'@HYPERLINK");
    }

    @Test
    void deveNeutralizarFormulaIniciadaComMaisOuMenos() {
        assertThat(exportarCsv("+1+1")).contains("'+1+1");
        assertThat(exportarCsv("-1-1")).contains("'-1-1");
    }

    @Test
    void naoDeveAlterarDescricaoBenigna() {
        String csv = exportarCsv("Salario mensal");
        assertThat(csv).contains("Salario mensal");
        assertThat(csv).doesNotContain("'Salario");
    }
}
