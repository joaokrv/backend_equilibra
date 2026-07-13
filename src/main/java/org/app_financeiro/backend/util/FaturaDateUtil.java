package org.app_financeiro.backend.util;

import java.time.LocalDate;
import java.time.YearMonth;

/** Cálculos de datas de fatura — fonte única para evitar divergência entre services. */
public final class FaturaDateUtil {

    private FaturaDateUtil() {}

    /**
     * Mês da fatura a que uma transação pertence: compra no dia do fechamento ou depois
     * cai na fatura do mês seguinte. Fonte única — a pré-criação de faturas históricas e o
     * roteamento da importação DEVEM usar o mesmo cálculo que a criação lazy de faturas,
     * senão a transação cai numa fatura diferente da pré-criada.
     */
    public static YearMonth mesReferencia(LocalDate dataTransacao, int diaFechamento) {
        YearMonth base = YearMonth.from(dataTransacao);
        return dataTransacao.getDayOfMonth() >= diaFechamento ? base.plusMonths(1) : base;
    }

    public static LocalDate calcularDataFechamento(int ano, int mes, int diaFechamento) {
        return comLimite(YearMonth.of(ano, mes), diaFechamento);
    }

    public static LocalDate calcularDataVencimento(int ano, int mes, int diaFechamento, int diaVencimento) {
        YearMonth ym = YearMonth.of(ano, mes);
        if (diaVencimento < diaFechamento) {
            ym = ym.plusMonths(1);
        }
        return comLimite(ym, diaVencimento);
    }

    private static LocalDate comLimite(YearMonth ym, int diaDesejado) {
        return LocalDate.of(ym.getYear(), ym.getMonthValue(), Math.min(diaDesejado, ym.lengthOfMonth()));
    }
}
