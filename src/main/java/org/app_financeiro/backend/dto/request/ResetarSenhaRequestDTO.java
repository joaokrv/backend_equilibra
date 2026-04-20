package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para resetar a senha do usuário.
 * Contém o token de recuperação e a nova senha com validações de complexidade.
 */
public record ResetarSenhaRequestDTO(
    @NotBlank(message = "O token de recuperação é obrigatório")
    String token,

    @NotBlank(message = "A nova senha é obrigatória")
    @Size(min = 8, message = "A senha deve conter no mínimo 8 caracteres")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
        message = "A senha deve conter pelo menos uma letra maiúscula, uma minúscula, um número e um caractere especial (@$!%*?&)"
    )
    String novaSenha
) {}
