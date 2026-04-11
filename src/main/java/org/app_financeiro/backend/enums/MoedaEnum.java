package org.app_financeiro.backend.enums;

import lombok.Getter;

/**
 * Enumeração das moedas suportadas pelo sistema.
 * Gerencia o símbolo e a descrição para exibição no frontend.
 */
@Getter
public enum MoedaEnum {
    BRL("Real", "R$"),
    USD("Dólar", "$");

    private final String descricao;
    private final String simbolo;

    MoedaEnum(String descricao, String simbolo) {
        this.descricao = descricao;
        this.simbolo = simbolo;
    }
}
