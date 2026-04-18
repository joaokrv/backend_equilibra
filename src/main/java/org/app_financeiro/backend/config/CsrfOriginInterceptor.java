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
 * Bloqueia CSRF em endpoints que lêem cookie HttpOnly (refresh/logout).
 * SameSite=None (Vercel→Render) remove proteção nativa do browser — validação explícita necessária.
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
