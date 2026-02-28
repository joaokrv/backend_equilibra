package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TransacaoRegistroRequestDTO {

    @NotBlank(message = "A descrição é obrigatória")
    private String descricao;

    @NotNull(message = "O valor é obrigatório")
    @Positive(message = "O valor deve ser maior que zero")
    private BigDecimal valor;

    @NotNull(message = "A data da transação é obrigatória")
    private LocalDate data;

    @NotNull(message = "O tipo (RECEITA/DESPESA) é obrigatório")
    private TipoTransacao tipo;

    private StatusTransacao status; // Opcional: default PENDENTE no Service se vier null

    private MetodoPagamento metodoPagamento;

    // IDs das entidades relacionadas
    private Long contaId;
    private Long cartaoId;
    private Long categoriaId;

    private Integer numeroParcela;
    private Integer totalParcelas;
}
