package org.app_financeiro.backend.dto.response;

/**
 * Retorno do processo de login, contendo os tokens de autenticação JWT.
 *
 * @param accessToken   Token de acesso (curta duração) para uso em cada request
 * @param refreshToken  Token de renovação (longa duração) para obter novo accessToken
 * @param expiresIn     Tempo de expiração do accessToken em milissegundos
 */
public record AuthResponseDTO(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
