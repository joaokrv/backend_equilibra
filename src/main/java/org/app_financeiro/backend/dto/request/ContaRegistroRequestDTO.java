package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO para requisição de registro de uma nova conta bancária.
 *
 * <p>{@code investimentoInicial} (opcional) permite criar a conta e um investimento
 * inicial atomicamente na mesma transação — se o investimento falhar, a conta também
 * é revertida (sem saldo fantasma).
 */
public record ContaRegistroRequestDTO(
    @NotBlank(message = "O nome da conta é obrigatório")
    @Size(max = 100, message = "O nome da conta não pode exceder 100 caracteres")
    String nome,

    BigDecimal saldo,

    @DecimalMin(value = "0.00", message = "O investimento inicial não pode ser negativo")
    BigDecimal investimentoInicial,

    @Size(max = 255, message = "A descrição do investimento inicial não pode exceder 255 caracteres")
    String investimentoInicialDescricao
) {
    /** Criação simples de conta, sem investimento inicial. */
    public ContaRegistroRequestDTO(String nome, BigDecimal saldo) {
        this(nome, saldo, null, null);
    }
}
