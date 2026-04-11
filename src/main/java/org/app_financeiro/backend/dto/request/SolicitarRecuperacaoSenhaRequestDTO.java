package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para solicitar a recuperação de senha.
 * Contém apenas o e-mail do usuário que deseja resetar a senha.
 */
public record SolicitarRecuperacaoSenhaRequestDTO(
    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    String email
) {}
