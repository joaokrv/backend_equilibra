package org.app_financeiro.backend.dto.response;

/** Foto de perfil em base64 + content-type, para exibição no cliente. */
public record FotoResponseDTO(
    String fotoBase64,
    String contentType
) {}
