package org.app_financeiro.backend.dto.response;

import lombok.Data;
import org.app_financeiro.backend.entity.ContaEntity;

import java.math.BigDecimal;

/**
 * DTO de resposta com os dados de uma conta bancária do usuário.
 */
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
