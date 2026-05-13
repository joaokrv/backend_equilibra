package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.app_financeiro.backend.enums.TipoFiltroRelatorio;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record RelatorioFiltroDTO(
        @NotNull(message = "A data de início é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataInicio,

        @NotNull(message = "A data de fim é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataFim,

        @NotNull(message = "O tipo do filtro é obrigatório")
        TipoFiltroRelatorio tipoFiltro,

        StatusTransacao statusTransacao
) {
    @AssertTrue(message = "O intervalo máximo permitido para relatórios é de 12 meses")
    public boolean isIntervaloValido() {
        if (dataInicio == null || dataFim == null) return true;
        return ChronoUnit.MONTHS.between(dataInicio, dataFim) <= 12;
    }

    @AssertTrue(message = "A data de fim não pode ser anterior à data de início")
    public boolean isDataFimValida() {
        if (dataInicio == null || dataFim == null) return true;
        return !dataFim.isBefore(dataInicio);
    }
}
