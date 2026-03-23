package org.app_financeiro.backend.dto.response;

/**
 * DTO de resposta com os dados públicos de um usuário.
 */
public record UsuarioResponseDTO(
    Long id,
    String nome,
    String email
) {}
