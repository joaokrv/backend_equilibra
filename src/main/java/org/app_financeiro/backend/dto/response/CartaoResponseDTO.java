package org.app_financeiro.backend.dto.response;

import java.math.BigDecimal;

/**
 * DTO de resposta com os dados de um cartão de crédito do usuário.
 */
public record CartaoResponseDTO(
    Long id,
    String nome,
    BigDecimal limite,
    BigDecimal limiteDisponivel,
    Integer diaFechamento,
    Integer diaVencimento
) {}
