package org.app_financeiro.backend.dto.importacao;

import org.app_financeiro.backend.enums.FormatoDetectado;

import java.util.List;
import java.util.UUID;

/** Resposta ao upload: sessão criada com candidatas para revisão. */
public record ImportacaoIniciadaDTO(
        UUID importacaoId,
        FormatoDetectado formatoDetectado,
        List<TransacaoCandidataDTO> candidatas,
        int totalCandidatas,
        int totalDuplicatas,
        /** Verdadeiro se alguma candidata teve ano presumido — sinaliza para o frontend exibir o seletor de ano. */
        boolean contemDataPresumida
) {}
