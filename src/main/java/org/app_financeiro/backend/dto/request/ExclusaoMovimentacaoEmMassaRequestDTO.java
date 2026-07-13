package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Lote de movimentações de investimento a excluir de uma vez (seleção múltipla no Histórico de Investimentos). */
public record ExclusaoMovimentacaoEmMassaRequestDTO(
        @NotNull @Size(min = 1, max = 100) List<@NotNull Long> movimentacaoIds
) {}
