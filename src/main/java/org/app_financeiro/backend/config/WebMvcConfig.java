package org.app_financeiro.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final CsrfOriginInterceptor csrfOriginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Rate limit apenas em endpoints sensíveis — mercado excluído para evitar 429 indevido em cadastro/verificação.
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/auth/login",
                        "/api/auth/registrar",
                        "/api/auth/refresh",
                        "/api/auth/reenviar-codigo",
                        "/api/auth/reativar-conta",
                        "/api/auth/solicitar-recuperacao",
                        "/api/auth/resetar-senha",
                        "/api/auth/verificar-email",
                        "/api/auth/validar-token",
                        "/api/usuarios/perfil/me/senha",
                        "/api/usuarios/perfil/me/solicitar-alteracao-email",
                        "/api/market/**",
                        "/api/mercado/**"
                );

        // CSRF por Origin em endpoints que lêem cookie HttpOnly (SameSite=None não protege cross-origin).
        registry.addInterceptor(csrfOriginInterceptor)
                .addPathPatterns(
                        "/api/auth/refresh",
                        "/api/auth/logout"
                );
    }
}
