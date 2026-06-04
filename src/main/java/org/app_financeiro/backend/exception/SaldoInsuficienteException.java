package org.app_financeiro.backend.exception;

/** Débito maior que saldo disponível. → 422 UNPROCESSABLE_ENTITY. */
public class SaldoInsuficienteException extends RegraDeNegocioException {

    public SaldoInsuficienteException(String mensagem) {
        super(mensagem);
    }

    public SaldoInsuficienteException(java.math.BigDecimal saldoDisponivel) {
        super("error.conta.saldo_insuficiente",
              "Saldo insuficiente. Disponível: R$ " + saldoDisponivel,
              saldoDisponivel);
    }
}
