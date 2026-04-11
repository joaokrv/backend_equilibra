package org.app_financeiro.backend.dto.response;

import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de resposta com os dados de uma transação financeira.
 * Retorna nomes descritivos e IDs de categoria, conta e cartão para suporte a edição.
 */
public record TransacaoResponseDTO(
    Long id,
    String descricao,
    BigDecimal valor,
    LocalDate data,
    TipoTransacao tipo,
    StatusTransacao status,
    MetodoPagamento metodoPagamento,
    String nomeCategoria,
    Long categoriaId,
    String nomeConta,
    Long contaId,
    String nomeCartao,
    Long cartaoId,
    boolean isRecorrente
) {}
