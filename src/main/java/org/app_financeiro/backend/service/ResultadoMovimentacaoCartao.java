package org.app_financeiro.backend.service;

import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;

/**
 * Record auxiliar para encapsular o resultado de uma movimentação em cartão de crédito.
 * Evita retornar tipos complexos ou arrays não tipados entre services.
 */
public record ResultadoMovimentacaoCartao(CartaoEntity cartao, FaturaEntity fatura) {}
