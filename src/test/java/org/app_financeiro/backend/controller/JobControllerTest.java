package org.app_financeiro.backend.controller;

import org.app_financeiro.backend.AbstractIntegrationTest;
import org.app_financeiro.backend.dto.response.JobResultDTO;
import org.app_financeiro.backend.service.FaturaLembreteService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = "job.token=token-valido-teste")
class JobControllerTest extends AbstractIntegrationTest {

    @MockBean
    private FaturaLembreteService faturaLembreteService;

    @Test
    void deveRetornar200ComTokenValido() throws Exception {
        when(faturaLembreteService.executarJob()).thenReturn(new JobResultDTO(5, 4, 1, 0));

        mockMvc.perform(post("/internal/jobs/faturas/lembrar")
                .header("X-Job-Token", "token-valido-teste")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(4))
                .andExpect(jsonPath("$.processed").value(5))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.errors").value(0));
    }

    @Test
    void deveRetornar401ComTokenInvalido() throws Exception {
        mockMvc.perform(post("/internal/jobs/faturas/lembrar")
                .header("X-Job-Token", "token-errado")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(post("/internal/jobs/faturas/lembrar")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }
}
