package org.app_financeiro.backend.dto.response;

import java.math.BigDecimal;

/**
 * DTO de resposta com os dados de um investimento ou meta de poupança.
 */
public record InvestimentoResponseDTO(
    Long id,
    String descricao,
    BigDecimal valorInicial,
    BigDecimal valorAtual,
    BigDecimal metaAtual
) {}
