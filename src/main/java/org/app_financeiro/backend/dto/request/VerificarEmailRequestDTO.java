package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerificarEmailRequestDTO {

    @NotBlank(message = "O e-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    private String email;

    @NotBlank(message = "O código de verificação é obrigatório")
    @Size(min = 6, max = 6, message = "O código deve ter exatamente 6 dígitos")
    private String codigo;
}
