package org.app_financeiro.backend.enums;

/**
 * Enum que representa o tipo de uma transação financeira.
 *
 * RECEITA: entrada de dinheiro — credita o saldo de uma conta.
 * DESPESA: saída de dinheiro — debita o saldo de uma conta ou consome o limite de um cartão.
 *
 * Também é usado em CategoriaEntity para tipar categorias,
 * impedindo que categorias de DESPESA sejam aplicadas a transações de RECEITA.
 */
public enum TipoTransacao {
    RECEITA,
    DESPESA
}
