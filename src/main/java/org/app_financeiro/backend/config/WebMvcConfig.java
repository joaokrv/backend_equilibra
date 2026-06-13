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
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/auth/login",
                        "/api/auth/logout",
                        "/api/auth/verificar-email",
                        "/api/auth/pre-registrar",
                        "/api/auth/otp-status",
                        "/api/auth/refresh",
                        "/api/auth/reenviar-codigo",
                        "/api/auth/solicitar-acao-conta",
                        "/api/auth/excluir-conta",
                        "/api/auth/desativar-conta",
                        "/api/auth/reativar-conta",
                        "/api/auth/solicitar-recuperacao",
                        "/api/auth/resetar-senha",
                        "/api/auth/validar-token",
                        "/api/usuarios/perfil/me/senha",
                        "/api/usuarios/perfil/me/solicitar-alteracao-email",
                        "/api/market/**",
                        "/api/mercado/**",
                        "/actuator/health",
                        "/api/transacoes/**",
                        "/api/contas/**",
                        "/api/cartoes/**",
                        "/api/investimentos/**",
                        "/api/faturas/**",
                        "/api/categorias/**",
                        "/api/transacoes-recorrentes/**"
                );

        registry.addInterceptor(csrfOriginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/registrar",
                        "/api/auth/pre-registrar",
                        "/api/auth/verificar-email",
                        "/api/auth/reenviar-codigo",
                        "/api/auth/solicitar-recuperacao",
                        "/api/auth/resetar-senha",
                        "/api/auth/validar-token",
                        "/api/auth/otp-status"
                );
    }
}
