package org.app_financeiro.backend.util;

import java.time.LocalDate;
import java.time.YearMonth;

/** Cálculos de datas de fatura — fonte única para evitar divergência entre services. */
public final class FaturaDateUtil {

    private FaturaDateUtil() {}

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
