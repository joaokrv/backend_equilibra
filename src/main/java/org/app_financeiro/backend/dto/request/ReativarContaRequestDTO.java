package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * DTO para requisição de reativação de conta previamente desativada.
 *
 * @param email E-mail da conta inativa
 * @param senha Senha para confirmação de identidade
 */
public record ReativarContaRequestDTO(
    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "E-mail inválido")
    String email,

    @NotBlank(message = "A senha é obrigatória")
    String senha
) {}
