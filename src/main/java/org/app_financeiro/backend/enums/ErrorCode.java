package org.app_financeiro.backend.enums;

/**
 * Códigos de erro utilizados nas respostas JSON do GlobalExceptionHandler.
 * Cada valor tem a chave correspondente no arquivo de mensagens.
 */
public enum ErrorCode {
    SALDO_INSUFICIENTE("error.saldo_insuficiente"),
    LIMITE_INSUFICIENTE("error.limite_insuficiente"),
    OPERACAO_NAO_PERMITIDA("error.operacao_nao_permitida"),
    EMAIL_NAO_VERIFICADO("error.email_nao_verificado"),
    EMAIL_JA_CADASTRADO("error.email_ja_cadastrado"),
    REGISTRO_NAO_ENCONTRADO("error.registro_nao_encontrado"),
    CREDENCIAIS_INVALIDAS("error.credenciais_invalidas"),
    REGRA_DE_NEGOCIO("error.regra_de_negocio"),
    CONTA_JA_ATIVA("error.conta_ja_ativa"),
    RATE_LIMIT_EXCEEDED("error.rate_limit_excedido");

    private final String messageKey;

    ErrorCode(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
