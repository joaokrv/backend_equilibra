package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmarAcaoContaRequestDTO(

    @NotBlank(message = "A senha é obrigatória")
    String senha,

    @NotBlank(message = "O código de confirmação é obrigatório")
    @Size(min = 6, max = 6, message = "O código deve ter exatamente 6 dígitos")
    String codigo
) {}
