package org.app_financeiro.backend.dto.response;

import lombok.Data;
import org.app_financeiro.backend.entity.UsuarioEntity;

@Data
public class UsuarioResponseDTO {
    private Long id;
    private String nome;
    private String email;

    public UsuarioResponseDTO(UsuarioEntity entity) {
        this.id = entity.getId();
        this.nome = entity.getNome();
        this.email = entity.getEmail();
    }
}
