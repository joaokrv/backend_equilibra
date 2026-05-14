package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para requisição de registro de uma nova transação financeira.
 * Suporta transações via conta bancária ou cartão de crédito, com categoria opcional.
 */
public record TransacaoRegistroRequestDTO(
    @NotBlank(message = "A descrição é obrigatória")
    @Size(max = 255, message = "A descrição não pode exceder 255 caracteres")
    String descricao,

    @NotNull(message = "O valor é obrigatório")
    @Positive(message = "O valor deve ser maior que zero")
    BigDecimal valor,

    @NotNull(message = "A data da transação é obrigatória")
    LocalDate data,

    @NotNull(message = "O tipo (RECEITA/DESPESA) é obrigatório")
    TipoTransacao tipo,

    StatusTransacao status,

    MetodoPagamento metodoPagamento,

    Long contaId,
    Long cartaoId,
    Long categoriaId,
    Long recorrenteId,

    @Min(value = 1, message = "O número da parcela deve ser no mínimo 1")
    Integer numeroParcela,

    @Min(value = 1, message = "O total de parcelas deve ser no mínimo 1")
    Integer totalParcelas,

    @NotBlank(message = "A chave de idempotência é obrigatória para prevenir duplicações")
    @Size(max = 100, message = "A chave de idempotência não pode exceder 100 caracteres")
    String idempotencyKey
) {}
