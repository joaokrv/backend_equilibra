package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransacaoRecorrenteRequestDTO(
    @NotBlank(message = "A descrição é obrigatória")
    @Size(max = 255, message = "A descrição não pode exceder 255 caracteres")
    String descricao,

    @NotNull(message = "O valor é obrigatório")
    @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
    BigDecimal valor,

    @NotNull(message = "O tipo é obrigatório")
    TipoTransacao tipo,

    @NotNull(message = "O método de pagamento é obrigatório")
    MetodoPagamento metodoPagamento,

    @NotNull(message = "A conta é obrigatória")
    Long contaId,

    Long cartaoId,

    Long categoriaId,

    @NotNull(message = "O dia de lançamento é obrigatório")
    @Min(value = 1, message = "O dia deve ser entre 1 e 31")
    @Max(value = 31, message = "O dia deve ser entre 1 e 31")
    Integer diaLancamento,

    LocalDate dataInicio,

    LocalDate dataFim
) {}
