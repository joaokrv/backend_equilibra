package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SolicitarAcaoContaRequestDTO(

    @NotBlank(message = "A ação é obrigatória")
    @Pattern(regexp = "EXCLUIR|DESATIVAR", message = "Ação deve ser EXCLUIR ou DESATIVAR")
    String acao,

    @NotBlank(message = "A senha é obrigatória")
    String senha
) {}
