package org.app_financeiro.backend.service;

import org.app_financeiro.backend.AbstractIntegrationTest;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anti-enumeração: pré-registro com e-mail já cadastrado retorna 200 fake
 * e dispara aviso ao dono real do endereço. Throttle de 1h impede spam.
 */
class AvisoTentativaCadastroIntegrationTest extends AbstractIntegrationTest {

    @SpyBean
    private ExternalEmailSenderService externalEmailSenderService;

    @Test
    void preRegistroComEmailJaCadastradoEnviaAvisoUmaUnicaVezPorThrottle() throws Exception {
        String email = "joao.aviso@example.com";
        setupUsuarioVerificado("João Aviso", email, "Senha@1234");

        reset(externalEmailSenderService);

        UsuarioRegistroRequestDTO tentativa = new UsuarioRegistroRequestDTO("Atacante", email, "OutraSenha@1");
        String body = objectMapper.writeValueAsString(tentativa);

        mockMvc.perform(post("/api/auth/pre-registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<String> assuntoCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        verify(externalEmailSenderService, times(1))
                .sendHtml(eq(email), assuntoCaptor.capture(), htmlCaptor.capture());

        org.junit.jupiter.api.Assertions.assertTrue(
                assuntoCaptor.getValue().contains("Tentativa de criação de conta"),
                "Assunto deve indicar tentativa de criação de conta");
        org.junit.jupiter.api.Assertions.assertTrue(
                htmlCaptor.getValue().contains("Tentativa de criação de conta"),
                "Corpo HTML deve refletir o template de aviso");

        mockMvc.perform(post("/api/auth/pre-registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(externalEmailSenderService, times(1))
                .sendHtml(eq(email), anyString(), anyString());
    }
}
