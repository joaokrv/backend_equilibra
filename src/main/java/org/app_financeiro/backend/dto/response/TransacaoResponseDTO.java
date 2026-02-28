package org.app_financeiro.backend.dto.response;

import lombok.Data;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TransacaoResponseDTO {
    private Long id;
    private String descricao;
    private BigDecimal valor;
    private LocalDate data;
    private TipoTransacao tipo;
    private StatusTransacao status;
    private MetodoPagamento metodoPagamento;
    private String nomeCategoria; // Simpler to return just the name for the frontend
    private String nomeConta;
    private String nomeCartao;

    public TransacaoResponseDTO(TransacaoEntity entity) {
        this.id = entity.getId();
        this.descricao = entity.getDescricao();
        this.valor = entity.getValor();
        this.data = entity.getData();
        this.tipo = entity.getTipo();
        this.status = entity.getStatus();
        this.metodoPagamento = entity.getMetodoPagamento();
        
        if (entity.getCategoria() != null) {
            this.nomeCategoria = entity.getCategoria().getNome();
        }
        if (entity.getConta() != null) {
            this.nomeConta = entity.getConta().getNome();
        }
        if (entity.getCartao() != null) {
            this.nomeCartao = entity.getCartao().getNome();
        }
    }
}
