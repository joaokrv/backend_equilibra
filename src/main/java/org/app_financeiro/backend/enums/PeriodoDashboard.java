package org.app_financeiro.backend.enums;

import org.app_financeiro.backend.exception.RegraDeNegocioException;

import java.util.Arrays;

/**
 * Janela temporal usada no resumo da dashboard.
 */
public enum PeriodoDashboard {
    UM_MES("1M", 1),
    TRES_MESES("3M", 3),
    SEIS_MESES("6M", 6),
    UM_ANO("1A", 12);

    private final String codigo;
    private final int quantidadeMeses;

    PeriodoDashboard(String codigo, int quantidadeMeses) {
        this.codigo = codigo;
        this.quantidadeMeses = quantidadeMeses;
    }

    public String getCodigo() {
        return codigo;
    }

    public int getQuantidadeMeses() {
        return quantidadeMeses;
    }

    public static PeriodoDashboard fromCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return UM_MES;
        }

        return Arrays.stream(values())
                .filter(item -> item.codigo.equalsIgnoreCase(codigo.trim()))
                .findFirst()
                .orElseThrow(() -> new RegraDeNegocioException(
                        "Periodo invalido. Valores aceitos: 1M, 3M, 6M, 1A"
                ));
    }
}
