package org.app_financeiro.backend.dto.response;

import org.app_financeiro.backend.enums.TipoMovimentacaoInvestimento;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovimentacaoInvestimentoResponseDTO(
        Long id,
        TipoMovimentacaoInvestimento tipo,
        BigDecimal valor,
        LocalDate data,
        String descricaoInvestimento,
        Long investimentoId,
        String nomeContaOrigem,
        String observacao
) {}
