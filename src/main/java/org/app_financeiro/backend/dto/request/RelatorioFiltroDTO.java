package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import org.app_financeiro.backend.enums.TipoFiltroRelatorio;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

public record RelatorioFiltroDTO(
        @NotNull(message = "A data de início é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) // Força o padrão yyyy-MM-dd
        LocalDate dataInicio,

        @NotNull(message = "A data de fim é obrigatória")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dataFim,

        @NotNull(message = "O tipo do filtro é obrigatório")
        TipoFiltroRelatorio tipoFiltro,

        StatusTransacao statusTransacao
) {}
