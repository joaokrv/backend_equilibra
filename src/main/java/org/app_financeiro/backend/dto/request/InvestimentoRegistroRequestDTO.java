package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.app_financeiro.backend.enums.TipoInvestimento;

import java.math.BigDecimal;

/**
 * DTO para requisição de registro de um novo investimento ou meta de poupança.
 */
public record InvestimentoRegistroRequestDTO(
    @NotBlank(message = "A descrição do investimento é obrigatória")
    String descricao,

    @NotNull(message = "O valor inicial é obrigatório")
    @DecimalMin(value = "0.00", message = "O valor inicial não pode ser negativo")
    BigDecimal valorInicial,

    @DecimalMin(value = "0.01", message = "A meta deve ser maior que zero")
    BigDecimal meta,

    @NotNull(message = "A conta de origem é obrigatória")
    Long contaId,

    Long contaDestinoId,

    @NotNull(message = "O tipo de investimento é obrigatório")
    TipoInvestimento tipoInvestimento,

    @Size(max = 60, message = "O tipo personalizado deve ter no máximo 60 caracteres")
    String tipoPersonalizado
) {}
