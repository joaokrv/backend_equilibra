package org.app_financeiro.backend.config;

import org.app_financeiro.backend.AbstractIntegrationTest;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.enums.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Valida o fluxo end-to-end do rate limit no escopo /api/auth/* (limite 5/min por IP).
 * Profile test desabilita rate-limit por padrão (application-test.properties:34);
 * este teste habilita explicitamente via @TestPropertySource.
 * @DirtiesContext: o teste exaure de propósito o bucket do IP de teste — sem isso, outras
 * classes com o mesmo @TestPropertySource reaproveitariam o contexto (e o RateLimitInterceptor
 * singleton, com buckets já consumidos), falhando o login do próprio setUp() delas.
 */
@TestPropertySource(properties = "security.rate-limit.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Test
    void sextaTentativaDeLoginRetorna429ComPayloadPadronizado() throws Exception {
        UsuarioLoginRequestDTO login = new UsuarioLoginRequestDTO("inexistente@example.com", "senha-qualquer");
        String body = objectMapper.writeValueAsString(login);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is4xxClientError());
        }

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
