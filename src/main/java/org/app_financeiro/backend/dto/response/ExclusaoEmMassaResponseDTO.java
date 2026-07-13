package org.app_financeiro.backend.dto.response;

import java.util.List;

/**
 * Resultado de uma exclusão em massa — cada item bloqueado (fatura paga, vínculo a investimento, IDOR)
 * vira uma entrada em erros, sem abortar o lote. Compartilhado entre TransacaoService.excluirEmMassa
 * (itemId = ID da transação) e InvestimentoService.excluirMovimentacoesEmMassa (itemId = ID da movimentação).
 */
public record ExclusaoEmMassaResponseDTO(int excluidas, List<ItemErroDTO> erros) {

    public record ItemErroDTO(Long itemId, String motivo) {}
}
