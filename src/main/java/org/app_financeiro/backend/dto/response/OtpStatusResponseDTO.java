package org.app_financeiro.backend.dto.response;

import java.time.OffsetDateTime;

/**
 * Representa o estado atual do OTP para o frontend.
 * Segue o princípio de "Fonte Única de Verdade no Backend"
 */
public record OtpStatusResponseDTO(
    String status,
    Integer tentativasRestantes,
    OffsetDateTime expiraEm,
    OffsetDateTime bloqueadoAte,
    OffsetDateTime proximoReenvioEm,
    OffsetDateTime proximaTentativaEm,
    String registroId
) {}
