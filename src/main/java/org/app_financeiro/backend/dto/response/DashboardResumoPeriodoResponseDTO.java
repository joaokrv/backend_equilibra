package org.app_financeiro.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Resumo da dashboard em uma janela temporal e comparacao contra a janela anterior.
 */
public record DashboardResumoPeriodoResponseDTO(
        String periodo,
        LocalDate inicioPeriodoAtual,
        LocalDate fimPeriodoAtual,
        LocalDate inicioPeriodoAnterior,
        LocalDate fimPeriodoAnterior,
        BigDecimal totalReceitasAtual,
        BigDecimal totalReceitasAnterior,
        BigDecimal totalReceitasPendentesAtual,
        Double variacaoReceitasPercentual,
        BigDecimal totalDespesasAtual,
        BigDecimal totalDespesasAnterior,
        BigDecimal totalDespesasPendentesAtual,
        Double variacaoDespesasPercentual,
        BigDecimal saldoContasAtual,
        BigDecimal totalInvestidoAtual,
        Double variacaoSaldoContasPercentual,
        Double variacaoInvestimentosPercentual
) {
}
