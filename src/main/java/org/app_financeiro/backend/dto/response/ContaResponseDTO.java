package org.app_financeiro.backend.dto.response;

import lombok.Data;
import org.app_financeiro.backend.entity.ContaEntity;

import java.math.BigDecimal;

@Data
public class ContaResponseDTO {
    private Long id;
    private String nome;
    private BigDecimal saldo;

    public ContaResponseDTO(ContaEntity entity) {
        this.id = entity.getId();
        this.nome = entity.getNome();
        this.saldo = entity.getSaldo();
    }
}
