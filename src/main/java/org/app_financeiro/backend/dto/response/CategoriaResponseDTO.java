package org.app_financeiro.backend.dto.response;

import lombok.Data;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.enums.TipoTransacao;

@Data
public class CategoriaResponseDTO {
    private Long id;
    private String nome;
    private TipoTransacao tipo;

    public CategoriaResponseDTO(CategoriaEntity entity) {
        this.id = entity.getId();
        this.nome = entity.getNome();
        this.tipo = entity.getTipo();
    }
}
