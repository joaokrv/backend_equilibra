package org.app_financeiro.backend.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CsrfOriginInterceptorTest {

    private static final String ALLOWED_ORIGINS = "http://localhost:5173,https://app.equilibra.com";

    private CsrfOriginInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CsrfOriginInterceptor(ALLOWED_ORIGINS);
    }

    /** Requisição mutante (POST) autenticada por cookie — caso sujeito à validação de origem. */
    private MockHttpServletRequest mutacaoComCookieDeSessao() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setCookies(new Cookie("accessToken", "fake-jwt-token"));
        return request;
    }

    @Test
    void devePermitirOrigemValida() throws Exception {
        MockHttpServletRequest request = mutacaoComCookieDeSessao();
        request.addHeader("Origin", "http://localhost:5173");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void devePermitirOrigemDeProducaoValida() throws Exception {
        MockHttpServletRequest request = mutacaoComCookieDeSessao();
        request.addHeader("Origin", "https://app.equilibra.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
    }

    @Test
    void deveBloquearOrigemInvalida() throws Exception {
        MockHttpServletRequest request = mutacaoComCookieDeSessao();
        request.addHeader("Origin", "https://evil.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void deveBloquearSemOrigemSemReferer() throws Exception {
        MockHttpServletRequest request = mutacaoComCookieDeSessao();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void deveFazerFallbackParaRefererValido() throws Exception {
        MockHttpServletRequest request = mutacaoComCookieDeSessao();
        request.addHeader("Referer", "http://localhost:5173/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
    }

    @Test
    void deveBloquearRefererInvalido() throws Exception {
        MockHttpServletRequest request = mutacaoComCookieDeSessao();
        request.addHeader("Referer", "https://evil.com/page");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void deveBloquearRefererMalformado() throws Exception {
        MockHttpServletRequest request = mutacaoComCookieDeSessao();
        request.addHeader("Referer", "not-a-valid-url");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
    }

    @Test
    void deveBloquearOrigemNullString() throws Exception {
        MockHttpServletRequest request = mutacaoComCookieDeSessao();
        request.addHeader("Origin", "null");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    void deveIgnorarEspacosNasOrigensPermitidas() throws Exception {
        CsrfOriginInterceptor interceptorComEspacos = new CsrfOriginInterceptor(
                "http://localhost:5173 , https://app.equilibra.com");
        MockHttpServletRequest request = mutacaoComCookieDeSessao();
        request.addHeader("Origin", "https://app.equilibra.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptorComEspacos.preHandle(request, response, null);

        assertThat(result).isTrue();
    }

    @Test
    void devePermitirMetodoSeguroSemValidarOrigem() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setCookies(new Cookie("accessToken", "fake-jwt-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
    }

    @Test
    void devePermitirMutacaoSemCookieDeSessao() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.addHeader("Origin", "https://evil.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
    }
}
