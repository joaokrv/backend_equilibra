package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Lote de transações a excluir de uma vez (seleção múltipla no Extrato).
 * Teto de 100 alinhado ao maior pageSize da UI (50) com folga, sem abrir para abuso.
 */
public record ExclusaoEmMassaRequestDTO(
        @NotNull @Size(min = 1, max = 100) List<@NotNull Long> transacaoIds,

        /** Aplica a todos os itens do lote — não há opção de "excluir a compra inteira" por item em massa. */
        boolean grupo
) {}
