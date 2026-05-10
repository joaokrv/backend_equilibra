package org.app_financeiro.backend.dto.response;

import java.time.LocalDateTime;

/**
 * Representa o estado atual do OTP para o frontend.
 * Segue o princípio de "Fonte Única de Verdade no Backend"
 */
public record OtpStatusResponseDTO(
    String status,
    Integer tentativasRestantes,
    LocalDateTime expiraEm,
    LocalDateTime bloqueadoAte,
    LocalDateTime proximoReenvioEm,
    LocalDateTime proximaTentativaEm,
    String registroId
) {}
