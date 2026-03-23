package org.app_financeiro.backend.dto.response;

import org.app_financeiro.backend.enums.StatusFatura;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de resposta com os dados de uma fatura de cartão de crédito.
 * Inclui valorTotal, valorPago e o saldo restante para o frontend calcular progresso.
 */
public record FaturaResponseDTO(
    Long id,
    Long cartaoId,
    String cartaoNome,
    Integer mes,
    Integer ano,
    BigDecimal valorTotal,
    BigDecimal valorPago,
    BigDecimal valorRestante,
    StatusFatura status,
    LocalDate dataVencimento,
    LocalDate dataFechamento
) {}
