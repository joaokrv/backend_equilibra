package org.app_financeiro.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.app_financeiro.backend.dto.response.ErroResponseDTO;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Retorna 401 (não 403) para requisições sem autenticação válida em recursos protegidos.
 * Semântica correta para API stateless/JWT e necessária para o refresh silencioso do frontend (dispara em 401).
 * Requisições autenticadas porém sem permissão continuam retornando 403 (AccessDeniedHandler padrão).
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String msg = messageSource.getMessage(
                "error.nao_autenticado", null, "Authentication required.", LocaleContextHolder.getLocale());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErroResponseDTO body = new ErroResponseDTO(
                HttpStatus.UNAUTHORIZED.value(), "NAO_AUTENTICADO", "Não autenticado", msg);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
