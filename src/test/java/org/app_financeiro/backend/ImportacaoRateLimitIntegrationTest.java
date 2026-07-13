package org.app_financeiro.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REGRESSÃO (auditoria de segurança 2026-07-12): POST /api/importacao chama a API paga do
 * Gemini para PDF/CSV não reconhecido, mas o bucket dedicado (ESCOPO_UPLOAD, 8/hora) nunca
 * era consultado — o path "/api/importacao/**" estava ausente de WebMvcConfig.addInterceptors,
 * então o RateLimitInterceptor nunca era invocado para esse endpoint. Rate limit desabilitado
 * globalmente em application-test.properties; habilitado aqui via @TestPropertySource — mas isso
 * NÃO garante um contexto dedicado: outra classe com o mesmo @TestPropertySource reaproveitaria o
 * mesmo contexto (mesmo RateLimitInterceptor singleton), herdando buckets já consumidos. @DirtiesContext
 * força um contexto novo para quem rodar depois.
 */
@TestPropertySource(properties = "security.rate-limit.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Rate Limit — Importação")
class ImportacaoRateLimitIntegrationTest extends AbstractIntegrationTest {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        limparTodasAsTabelas();
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        token = setupUsuarioVerificado("Rate Limit Tester", "ratelimit@equilibra.test", "RateLimit@123Secure");
    }

    /** CSV Sicredi reconhecido localmente — evita depender do Gemini (sem API key em teste). */
    private MockMultipartFile csvReconhecido(int sufixo) {
        String conteudo = "Extrato Conta Corrente\n;;;\nAssociado: FULANO DE TAL\nCooperativa: 0000\nConta: 00000-0\n"
                + "Data Lançamento;Histórico;Descrição;Valor;Saldo\n"
                + "01/03/2026;;Compra Teste " + sufixo + ";-10,00;990,00\n";
        return new MockMultipartFile("arquivo", "extrato.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("9ª requisição de upload dentro da mesma hora é bloqueada com 429 (bucket de 8/hora)")
    void nonaRequisicaoDeUploadEhBloqueadaPeloRateLimit() throws Exception {
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(multipart("/api/importacao")
                            .file(csvReconhecido(i))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(multipart("/api/importacao")
                        .file(csvReconhecido(8))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());
    }
}
