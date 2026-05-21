package org.app_financeiro.backend.config;

import org.app_financeiro.backend.AbstractIntegrationTest;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.enums.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Valida o fluxo end-to-end do rate limit no escopo /api/auth/* (limite 3/min por IP).
 * Profile test desabilita rate-limit por padrão (application-test.properties:34);
 * este teste habilita explicitamente via @TestPropertySource.
 */
@TestPropertySource(properties = "security.rate-limit.enabled=true")
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Test
    void quartaTentativaDeLoginRetorna429ComPayloadPadronizado() throws Exception {
        UsuarioLoginRequestDTO login = new UsuarioLoginRequestDTO("inexistente@example.com", "senha-qualquer");
        String body = objectMapper.writeValueAsString(login);

        // 3 requisições consumindo o bucket (resposta esperada é 401 — usuário não existe — mas é o suficiente para gastar o bucket)
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is4xxClientError());
        }

        // 4ª requisição deve estourar o bucket e retornar 429
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value(ErrorCode.RATE_LIMIT_EXCEEDED.name()))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.mensagem").isNotEmpty())
                .andReturn();

        String retryAfter = result.getResponse().getHeader("Retry-After");
        long retryAfterSeconds = Long.parseLong(retryAfter);
        org.junit.jupiter.api.Assertions.assertTrue(retryAfterSeconds >= 1,
                "Retry-After deve indicar pelo menos 1 segundo de espera, recebido: " + retryAfterSeconds);
    }
}
