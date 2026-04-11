package org.app_financeiro.backend.dto.response;

import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransacaoRecorrenteResponseDTO(
    Long id,
    String descricao,
    BigDecimal valor,
    TipoTransacao tipo,
    MetodoPagamento metodoPagamento,
    String nomeConta,
    String nomeCartao,
    String nomeCategoria,
    Integer diaLancamento,
    LocalDate dataInicio,
    LocalDate dataFim,
    boolean ativo
) {}
