package org.app_financeiro.backend.dto.response;

import lombok.Data;
import org.app_financeiro.backend.entity.CartaoEntity;

import java.math.BigDecimal;

/**
 * DTO de resposta com os dados de um cartão de crédito do usuário.
 */
@Data
public class CartaoResponseDTO {
    private Long id;
    private String nome;
    private BigDecimal limite;
    private BigDecimal limiteDisponivel;
    private Integer diaFechamento;
    private Integer diaVencimento;

    public CartaoResponseDTO(CartaoEntity entity, BigDecimal limiteDisponivel) {
        this.id = entity.getId();
        this.nome = entity.getNome();
        this.limite = entity.getLimite();
        this.limiteDisponivel = limiteDisponivel;
        this.diaFechamento = entity.getDiaFechamento();
        this.diaVencimento = entity.getDiaVencimento();
    }
}
