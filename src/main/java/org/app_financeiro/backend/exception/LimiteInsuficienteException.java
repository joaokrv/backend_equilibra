package org.app_financeiro.backend.exception;

/** Despesa excede limite disponível do cartão. → 422 UNPROCESSABLE_ENTITY. */
public class LimiteInsuficienteException extends RegraDeNegocioException {

    public LimiteInsuficienteException(String mensagem) {
        super(mensagem);
    }

    public LimiteInsuficienteException(java.math.BigDecimal limiteDisponivel) {
        super("error.cartao.limite_insuficiente",
              "Limite insuficiente no cartão. Disponível: R$ " + limiteDisponivel,
              limiteDisponivel);
    }
}
