package org.app_financeiro.backend.dto.response;

import org.app_financeiro.backend.enums.TipoTransacao;

/**
 * DTO de resposta com os dados de uma categoria de transação.
 */
public record CategoriaResponseDTO(
    Long id,
    String nome,
    TipoTransacao tipo
) {}
