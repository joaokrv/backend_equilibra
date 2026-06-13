package org.app_financeiro.backend.dto.response;

public record AuthResponseDTO(
        String accessToken,
        String refreshToken,
        long expiraEm,
        UsuarioResponseDTO usuario,
        OtpStatusResponseDTO otpStatus
) {}
