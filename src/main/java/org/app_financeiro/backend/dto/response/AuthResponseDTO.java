package org.app_financeiro.backend.dto.response;

import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;

public record AuthResponseDTO(
        String accessToken,
        String refreshToken,
        long expiraEm,
        UsuarioResponseDTO usuario,
        OtpStatusResponseDTO otpStatus
) {}
