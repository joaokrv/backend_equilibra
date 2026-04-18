package org.app_financeiro.backend.dto.response;

import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;

/** refreshToken sempre null no body — enviado via cookie HttpOnly (G5-A1). */
public record AuthResponseDTO(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UsuarioResponseDTO user
) {}
