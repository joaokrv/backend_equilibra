package org.app_financeiro.backend.dto.importacao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.app_financeiro.backend.enums.ClassificacaoCandidata;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.TipoTransacao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Candidata a transação extraída de um documento importado.
 * Preenchida pelo parser (CSV) ou pelo GeminiClient (PDF).
 * Enviada ao frontend para revisão antes da confirmação.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransacaoCandidataDTO(
        /** Índice da linha/item no documento original — compõe o idempotencyKey. */
        int indice,

        String descricao,
        BigDecimal valor,
        TipoTransacao tipo,

        /** Data da transação. Pode ter ano presumido — ver dataPresumida. */
        LocalDate data,

        /**
         * Verdadeiro quando o ano foi presumido (documento sem ano explícito, ex: PDF de fatura).
         * O frontend exibe banner para o usuário confirmar o ano em lote.
         */
        boolean dataPresumida,

        MetodoPagamento metodoPagamento,

        /** Número da parcela (ex: 1). Nulo se não parcelado. */
        Integer numeroParcela,

        /** Total de parcelas (ex: 12). Nulo se não parcelado. */
        Integer totalParcelas,

        /**
         * Indica suspeita de transação interna (Aplicação CDB, Resgate, Pagamento de Fatura).
         * Desmarcada por padrão na revisão — usuário escolhe incluir ou não.
         */
        boolean suspeita,

        /**
         * Verdadeiro se detectado como possível duplicata de transação existente.
         * Baseado em similarity(descricao) > 0.7 + data + valor via pg_trgm.
         */
        boolean duplicataDetectada,

        /**
         * Classificação atribuída na extração/enriquecimento — sempre revisável na UI.
         * Sessões JSONB antigas (sem o campo) deserializam como NORMAL.
         */
        ClassificacaoCandidata classificacao,

        /**
         * Sugestão automática de investimento para APORTE/RESGATE (match por nome).
         * Apenas pré-preenche o seletor na revisão — nunca decide sozinha.
         */
        Long investimentoSugeridoId
) {
    public TransacaoCandidataDTO {
        if (classificacao == null) {
            classificacao = ClassificacaoCandidata.NORMAL;
        }
    }

    /** Compatibilidade com chamadas anteriores à classificação (Fase 1). */
    public TransacaoCandidataDTO(int indice, String descricao, BigDecimal valor, TipoTransacao tipo,
                                 LocalDate data, boolean dataPresumida, MetodoPagamento metodoPagamento,
                                 Integer numeroParcela, Integer totalParcelas, boolean suspeita,
                                 boolean duplicataDetectada) {
        this(indice, descricao, valor, tipo, data, dataPresumida, metodoPagamento,
                numeroParcela, totalParcelas, suspeita, duplicataDetectada,
                ClassificacaoCandidata.NORMAL, null);
    }

    /** Cópia com nova classificação e sugestão de investimento, preservando os demais campos. */
    public TransacaoCandidataDTO comClassificacao(ClassificacaoCandidata novaClassificacao,
                                                  Long novoInvestimentoSugeridoId,
                                                  boolean novaSuspeita) {
        return new TransacaoCandidataDTO(indice, descricao, valor, tipo, data, dataPresumida,
                metodoPagamento, numeroParcela, totalParcelas, novaSuspeita, duplicataDetectada,
                novaClassificacao, novoInvestimentoSugeridoId);
    }

    /** Cópia marcada como possível duplicata, preservando os demais campos. */
    public TransacaoCandidataDTO comDuplicataDetectada() {
        return new TransacaoCandidataDTO(indice, descricao, valor, tipo, data, dataPresumida,
                metodoPagamento, numeroParcela, totalParcelas, suspeita, true,
                classificacao, investimentoSugeridoId);
    }

    /** Cópia com data resolvida (ano presumido aplicado ⇒ dataPresumida vira false). */
    public TransacaoCandidataDTO comDataDefinitiva(LocalDate novaData) {
        return new TransacaoCandidataDTO(indice, descricao, valor, tipo, novaData, false,
                metodoPagamento, numeroParcela, totalParcelas, suspeita, duplicataDetectada,
                classificacao, investimentoSugeridoId);
    }
}
