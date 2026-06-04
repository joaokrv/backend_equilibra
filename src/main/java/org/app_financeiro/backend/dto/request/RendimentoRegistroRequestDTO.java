package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RendimentoRegistroRequestDTO(
        @NotNull Long investimentoId,
        @NotNull BigDecimal valor,
        @NotNull @PastOrPresent LocalDate data,
        @Size(max = 255) String observacao
) {}
