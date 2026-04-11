package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para confirmar a alteração de e-mail com o código OTP.
 */
public record ConfirmarAlteracaoEmailRequestDTO(
    @NotBlank(message = "O código de verificação é obrigatório")
    @Size(min = 6, max = 6, message = "O código deve ter exatamente 6 dígitos")
    String codigo
) {}
