package org.app_financeiro.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Cota diária (global e por usuário — Fase 0.B) e segurança do transporte da API key.
 * Sem Spring context: RestTemplate real + MockRestServiceServer, campos @Value via reflection
 * (mesmo padrão de JwtServiceTest) — evita cache de contexto contaminar contadores entre testes.
 */
class GeminiClientTest {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";
    private static final String API_KEY = "chave-secreta-de-teste";

    private GeminiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        client = new GeminiClient(new RestTemplateBuilder(), new ObjectMapper());
        ReflectionTestUtils.setField(client, "apiKey", API_KEY);
        ReflectionTestUtils.setField(client, "maxChamadasDiarias", 20);
        ReflectionTestUtils.setField(client, "maxChamadasDiariasUsuario", 5);

        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    private String respostaGemini(String textoInterno) {
        return """
                {"candidates":[{"content":{"parts":[{"text":"%s"}]}}]}
                """.formatted(textoInterno.replace("\"", "\\\""));
    }

    @Test
    @DisplayName("Chamada bem-sucedida retorna o texto interno da resposta")
    void deveRetornarTextoQuandoChamadaBemSucedida() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(respostaGemini("ok"), MediaType.APPLICATION_JSON));

        String texto = client.extrairTexto(new byte[]{1, 2, 3}, "application/pdf", "prompt", 1L);

        assertThat(texto).isEqualTo("ok");
        server.verify();
    }

    @Test
    @DisplayName("apiKey em branco lança erro de configuração sem realizar chamada de rede")
    void deveLancarErroQuandoApiKeyEmBranco() {
        ReflectionTestUtils.setField(client, "apiKey", "");

        assertThatThrownBy(() -> client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 1L))
                .isInstanceOf(RegraDeNegocioException.class);
        // Nenhuma expectativa registrada no server: qualquer chamada HTTP real derrubaria o teste.
    }

    @Test
    @DisplayName("SEGURANÇA: a api key viaja pelo header x-goog-api-key, nunca pela query string da URL")
    void deveEnviarApiKeyViaHeaderNuncaViaQueryString() {
        server.expect(requestTo(ENDPOINT)) // match exato — sem "?key=..." no fim da URL
                .andExpect(header("x-goog-api-key", API_KEY))
                .andRespond(withSuccess(respostaGemini("ok"), MediaType.APPLICATION_JSON));

        client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 1L);

        server.verify();
    }

    @Test
    @DisplayName("ANTI-ABUSO (0.B): cota global esgotada bloqueia novas chamadas mesmo com folga individual")
    void deveBloquearQuandoCotaGlobalEsgotada() {
        ReflectionTestUtils.setField(client, "maxChamadasDiarias", 2);
        ReflectionTestUtils.setField(client, "maxChamadasDiariasUsuario", 10);
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(respostaGemini("a"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(respostaGemini("b"), MediaType.APPLICATION_JSON));

        client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 1L);
        client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 2L);

        assertThatThrownBy(() -> client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Limite diário");
    }

    @Test
    @DisplayName("ANTI-ABUSO (0.B): cota por usuário isola usuários — um esgotar a própria cota não afeta o outro")
    void deveIsolarCotaPorUsuario() {
        ReflectionTestUtils.setField(client, "maxChamadasDiarias", 10);
        ReflectionTestUtils.setField(client, "maxChamadasDiariasUsuario", 1);
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(respostaGemini("a"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(respostaGemini("b"), MediaType.APPLICATION_JSON));

        long usuarioA = 1L, usuarioB = 2L;
        client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", usuarioA);

        assertThatThrownBy(() -> client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", usuarioA))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Você atingiu");

        // Usuário B ainda tem cota própria — não é penalizado pelo consumo de A.
        String texto = client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", usuarioB);
        assertThat(texto).isEqualTo("b");
    }

    @Test
    @DisplayName("Contadores resetam na virada do dia, mesmo que a cota do dia anterior estivesse esgotada")
    void deveResetarContadoresNoDiaSeguinte() {
        ReflectionTestUtils.setField(client, "maxChamadasDiarias", 1);
        ReflectionTestUtils.setField(client, "maxChamadasDiariasUsuario", 1);
        ReflectionTestUtils.setField(client, "chamadasHoje", new AtomicInteger(1));
        ReflectionTestUtils.setField(client, "diaContador", LocalDate.now().minusDays(1));
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(respostaGemini("novo-dia"), MediaType.APPLICATION_JSON));

        String texto = client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 1L);

        assertThat(texto).isEqualTo("novo-dia");
    }

    @Test
    @DisplayName("Erro HTTP 4xx do Gemini retorna mensagem de falha de integração, sem vazar detalhes internos")
    void deveTratarErroHttp4xxComMensagemGenerica() {
        server.expect(requestTo(ENDPOINT))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Falha na integração")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    @DisplayName("Erro HTTP 5xx do Gemini cai no tratamento genérico, sem vazar a api key")
    void deveTratarErroHttp5xxComMensagemGenerica() {
        server.expect(requestTo(ENDPOINT)).andRespond(withServerError());

        assertThatThrownBy(() -> client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Erro inesperado")
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    @DisplayName("Falha de rede (sem resposta HTTP) não vaza a api key na mensagem da exceção")
    void deveTratarFalhaDeRedeSemVazarApiKey() {
        server.expect(requestTo(ENDPOINT)).andRespond(request -> { throw new IOException("conexão recusada"); });

        assertThatThrownBy(() -> client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageNotContaining(API_KEY);
    }

    @Test
    @DisplayName("Resposta com texto vazio lança erro de resposta vazia")
    void deveLancarErroQuandoTextoVazio() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(respostaGemini(""), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.extrairTexto(new byte[]{1}, "application/pdf", "prompt", 1L))
                .isInstanceOf(RegraDeNegocioException.class);
    }
}
