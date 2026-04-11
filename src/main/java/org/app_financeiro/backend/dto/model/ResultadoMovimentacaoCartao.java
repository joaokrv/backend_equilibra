package org.app_financeiro.backend.dto.model;

import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;

/**
 * Record auxiliar para encapsular os resultados de uma movimentação de cartão.
 * Contém a entidade do Cartão (com limite atualizado) e a Fatura afetada.
 */
public record ResultadoMovimentacaoCartao(CartaoEntity cartao, FaturaEntity fatura) {}
