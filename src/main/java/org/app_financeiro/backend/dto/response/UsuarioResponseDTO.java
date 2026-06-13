package org.app_financeiro.backend.dto.response;

import org.app_financeiro.backend.enums.MoedaEnum;

/**
 * DTO de resposta com os dados públicos de um usuário.
 */
public record UsuarioResponseDTO(
    Long id,
    String nome,
    String email,
    boolean isEmailVerificado,
    String celular,
    String fotoBase64,
    MoedaEnum moeda,
    boolean notificacoesFaturaAtivo
) {}
