package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para solicitar alteração de e-mail.
 * Exige a senha atual para confirmação de identidade.
 */
public record SolicitarAlteracaoEmailRequestDTO(
    @NotBlank(message = "O novo e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    String novoEmail,

    @NotBlank(message = "A senha atual é obrigatória")
    String senhaAtual
) {}
