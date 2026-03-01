package org.app_financeiro.backend.dto.response;

import lombok.Data;
import org.app_financeiro.backend.entity.InvestimentoEntity;

import java.math.BigDecimal;

/**
 * DTO de resposta com os dados de um investimento ou meta de poupança.
 */
@Data
public class InvestimentoResponseDTO {

    private Long id;
    private String descricao;
    private BigDecimal valorInicial;
    private BigDecimal valorAtual;
    private BigDecimal metaAtual;

    public InvestimentoResponseDTO(InvestimentoEntity entity) {
        this.id = entity.getId();
        this.descricao = entity.getDescricao();
        this.valorInicial = entity.getValorInicial();
        this.valorAtual = entity.getValorAtual();
        this.metaAtual = entity.getMetaAtual();
    }
}
