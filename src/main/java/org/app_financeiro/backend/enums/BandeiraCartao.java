package org.app_financeiro.backend.enums;

import lombok.Getter;

/**
 * Enum que define as bandeiras de cartão de crédito e instituições suportadas pelo sistema.
 * Utilizado para fins estéticos (ícones e cores) no Frontend.
 */
@Getter
public enum BandeiraCartao {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    ELO("Elo"),
    AMERICAN_EXPRESS("American Express"),
    DINERS_CLUB("Diners Club"),
    HIPERCARD("Hipercard"),
    NUBANK("Nubank"),
    INTER("Inter"),
    SICREDI("Sicredi"),
    OUROCARD("Ourocard"),
    DIGIO("Digio"),
    C6_BANK("C6 Bank"),
    BTG_PACTUAL("BTG Pactual"),
    XP_INVESTIMENTOS("XP Investimentos"),
    OUTROS("Outros");

    private final String descricao;

    BandeiraCartao(String descricao) {
        this.descricao = descricao;
    }
}
