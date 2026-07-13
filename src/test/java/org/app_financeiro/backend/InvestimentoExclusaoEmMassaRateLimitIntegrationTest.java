package org.app_financeiro.backend;

import org.app_financeiro.backend.dto.request.ExclusaoMovimentacaoEmMassaRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REGRESSÃO preventiva (mesmo padrão de TransacaoExclusaoEmMassaRateLimitIntegrationTest): confirma
 * que o endpoint de exclusão em massa de movimentações de investimento herda o bucket de escrita
 * CRUD (ESCOPO_ESCRITA, 40/min) já registrado para "/api/investimentos/**" — nunca assumir, sempre testar.
 * @DirtiesContext evita herdar buckets já consumidos de outra classe com o mesmo @TestPropertySource.
 */
@TestPropertySource(properties = "security.rate-limit.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Rate Limit — Exclusão em massa de movimentações de investimento")
class InvestimentoExclusaoEmMassaRateLimitIntegrationTest extends AbstractIntegrationTest {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        limparTodasAsTabelas();
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        token = setupUsuarioVerificado("Rate Limit Investimento", "ratelimitinvestimento@equilibra.test", "RateLimit@123Secure");
    }

    @Test
    @DisplayName("41ª requisição de exclusão em massa dentro do minuto é bloqueada com 429 (bucket de escrita, 40/min)")
    void quadragesimaPrimeiraRequisicaoEhBloqueadaPeloRateLimit() throws Exception {
        ExclusaoMovimentacaoEmMassaRequestDTO dto = new ExclusaoMovimentacaoEmMassaRequestDTO(List.of(999_999L));
        String body = objectMapper.writeValueAsString(dto);

        for (int i = 0; i < 40; i++) {
            mockMvc.perform(post("/api/investimentos/movimentacoes/excluir-em-massa")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/investimentos/movimentacoes/excluir-em-massa")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests());
    }
}
