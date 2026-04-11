package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.app_financeiro.backend.enums.MoedaEnum;

/**
 * DTO para requisição de atualização dos dados cadastrais do usuário.
 * Contém validações rigorosas para garantir a integridade e segurança dos dados.
 */
public record UsuarioAtualizacaoRequestDTO(
    @NotBlank(message = "O nome é obrigatório")
    @Size(min = 3, max = 100, message = "O nome deve conter entre 3 e 100 caracteres")
    String nome,

    @Pattern(regexp = "^\\d{10,11}$", message = "O celular deve conter apenas dígitos (10 ou 11 números)")
    String celular,

    @NotNull(message = "A moeda de preferência é obrigatória")
    MoedaEnum moeda
) {}
