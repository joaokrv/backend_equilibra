package org.app_financeiro.backend.dto.response;

import lombok.Data;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO de resposta com os dados de uma fatura de cartão de crédito.
 */
@Data
public class FaturaResponseDTO {
    private Long id;
    private Long cartaoId;
    private String cartaoNome;
    private Integer mes;
    private Integer ano;
    private BigDecimal valorTotal;
    private StatusFatura status;
    private LocalDate dataVencimento;
    private LocalDate dataFechamento;

    public FaturaResponseDTO(FaturaEntity entity) {
        this.id = entity.getId();
        if (entity.getCartao() != null) {
            this.cartaoId = entity.getCartao().getId();
            this.cartaoNome = entity.getCartao().getNome();
        }
        this.mes = entity.getMes();
        this.ano = entity.getAno();
        this.valorTotal = entity.getValorTotal();
        this.status = entity.getStatus();
        this.dataVencimento = entity.getDataVencimento();
        this.dataFechamento = entity.getDataFechamento();
    }
}