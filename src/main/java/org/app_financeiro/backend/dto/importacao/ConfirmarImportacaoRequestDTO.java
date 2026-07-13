package org.app_financeiro.backend.dto.importacao;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.util.ImportacaoConstantes;

import java.util.List;
import java.util.UUID;

/** Payload do frontend para confirmar quais candidatas devem virar transações. */
public record ConfirmarImportacaoRequestDTO(
        @NotNull UUID importacaoId,

        /** IDs de conta ou cartão destino. */
        Long contaId,
        Long cartaoId,

        /**
         * Ano a ser aplicado às transações com dataPresumida=true.
         * Nulo se não houver candidatas com ano presumido.
         */
        @Min(1990) @Max(2100) Integer anoPresumido,

        /** Apenas os índices que o usuário marcou como selecionados. */
        @NotNull @Size(max = ImportacaoConstantes.MAX_CANDIDATAS) List<@NotNull Integer> indicesSelecionados,

        /**
         * Vínculo investimento↔candidata para índices classificados como APORTE/RESGATE.
         * Sugestão automática apenas pré-preenche no frontend — este campo é a decisão final do usuário.
         */
        @Size(max = ImportacaoConstantes.MAX_CANDIDATAS) List<@Valid VinculoInvestimentoDTO> vinculosInvestimento,

        /** Índices que o usuário confirmou como transferência interna (isTransferencia=true, fora de receitas/despesas). */
        @Size(max = ImportacaoConstantes.MAX_CANDIDATAS) List<@NotNull Integer> indicesTransferencia,

        /**
         * Overrides de método/categoria por linha, aplicáveis apenas a candidatas NORMAL.
         * Ignorado para índices de APORTE/RESGATE/transferência (investimento não recebe categoria).
         */
        @Size(max = ImportacaoConstantes.MAX_CANDIDATAS) List<@Valid AjusteLinhaDTO> ajustesLinha
) {
    public ConfirmarImportacaoRequestDTO {
        if (vinculosInvestimento == null) vinculosInvestimento = List.of();
        if (indicesTransferencia == null) indicesTransferencia = List.of();
        if (ajustesLinha == null) ajustesLinha = List.of();
    }

    /** Confirmação sem investimentos/transferências/ajustes (a maioria dos casos: extrato comum). */
    public ConfirmarImportacaoRequestDTO(UUID importacaoId, Long contaId, Long cartaoId,
                                         Integer anoPresumido, List<Integer> indicesSelecionados) {
        this(importacaoId, contaId, cartaoId, anoPresumido, indicesSelecionados, List.of(), List.of(), List.of());
    }

    /** Confirmação com investimentos/transferências mas sem ajustes de método/categoria. */
    public ConfirmarImportacaoRequestDTO(UUID importacaoId, Long contaId, Long cartaoId, Integer anoPresumido,
                                         List<Integer> indicesSelecionados, List<VinculoInvestimentoDTO> vinculosInvestimento,
                                         List<Integer> indicesTransferencia) {
        this(importacaoId, contaId, cartaoId, anoPresumido, indicesSelecionados, vinculosInvestimento, indicesTransferencia, List.of());
    }

    /** Vínculo de uma candidata APORTE/RESGATE a um investimento existente do usuário. */
    public record VinculoInvestimentoDTO(
            @NotNull Integer indice,
            @NotNull Long investimentoId,

            /**
             * true: soma/subtrai do valorAtual do investimento (aporte/resgate ainda não refletido no saldo cadastrado).
             * false: registra apenas o histórico (saldo cadastrado já contempla este movimento).
             */
            boolean atualizarValor
    ) {}

    /** Override opcional de método de pagamento e/ou categoria para uma candidata NORMAL. */
    public record AjusteLinhaDTO(
            @NotNull Integer indice,
            MetodoPagamento metodoPagamento,
            Long categoriaId
    ) {}
}
