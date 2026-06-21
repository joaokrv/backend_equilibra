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
    MoedaEnum moeda,
    boolean notificacoesFaturaAtivo
) {}
