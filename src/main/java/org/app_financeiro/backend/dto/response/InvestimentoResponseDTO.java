package org.app_financeiro.backend.dto.response;

import org.app_financeiro.backend.enums.TipoInvestimento;

import java.math.BigDecimal;

/**
 * DTO de resposta com os dados de um investimento ou meta de poupança.
 */
public record InvestimentoResponseDTO(
    Long id,
    String descricao,
    TipoInvestimento tipoInvestimento,
    String tipoPersonalizado,
    BigDecimal valorInicial,
    BigDecimal valorAtual,
    BigDecimal metaAtual,
    String nomeContaOrigem,
    String nomeContaDestino
) {}
