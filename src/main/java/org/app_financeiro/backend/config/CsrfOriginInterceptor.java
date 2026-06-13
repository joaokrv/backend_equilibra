package org.app_financeiro.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bloqueia CSRF em mutações autenticadas por cookie HttpOnly.
 * Como o access token trafega em cookie SameSite=None (Vercel→Render), o browser o envia
 * em requisições cross-site — a proteção nativa do SameSite não existe. Estratégia:
 * <ul>
 *   <li>Métodos seguros (GET/HEAD/OPTIONS) passam — não alteram estado.</li>
 *   <li>Sem cookie de sessão (ex.: auth via header Bearer) passa — não é CSRF-able.</li>
 *   <li>Com cookie de sessão, exige Origin/Referer de origem confiável.</li>
 * </ul>
 */
@Component
public class CsrfOriginInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(CsrfOriginInterceptor.class);

    private final Set<String> allowedOrigins;

    public CsrfOriginInterceptor(
            @Value("${cors.allowed-origins}") String allowedOriginsRaw) {
        this.allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }

        if (!temCookieDeSessao(request)) {
            return true;
        }

        String origin = request.getHeader("Origin");

        if (origin == null) {
            origin = extractOriginFromReferer(request.getHeader("Referer"));
        }

        if (origin == null || !allowedOrigins.contains(origin)) {
            log.warn("CSRF bloqueado: origin={}, uri={}", origin, request.getRequestURI());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"erro\":\"Requisição bloqueada por proteção CSRF.\"}");
            return false;
        }

        return true;
    }

    private boolean temCookieDeSessao(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        return Arrays.stream(cookies)
                .anyMatch(c -> "accessToken".equals(c.getName()) || "refreshToken".equals(c.getName()));
    }

    private String extractOriginFromReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port != -1 ? ":" + port : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
