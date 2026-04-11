package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.app_financeiro.backend.enums.TipoInvestimento;

import java.math.BigDecimal;

/**
 * DTO para atualização completa da meta de investimento.
 */
public record InvestimentoAtualizacaoRequestDTO(
        @NotBlank(message = "O nome da meta é obrigatório")
        @Size(max = 100, message = "O nome da meta deve ter no máximo 100 caracteres")
        String descricao,

        @DecimalMin(value = "0.01", message = "A meta deve ser maior que zero")
        BigDecimal meta,

        @NotNull(message = "O tipo de investimento é obrigatório")
        TipoInvestimento tipoInvestimento,

        @Size(max = 60, message = "O tipo personalizado deve ter no máximo 60 caracteres")
        String tipoPersonalizado
) {
}
