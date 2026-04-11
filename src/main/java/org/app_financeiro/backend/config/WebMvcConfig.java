package org.app_financeiro.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra interceptadores na cadência global do Servidor Spring WebMVC.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Bloqueando exclusivamente acessos às tentativas de registro e verificação
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
    }
}
