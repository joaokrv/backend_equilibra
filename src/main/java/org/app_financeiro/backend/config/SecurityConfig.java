package org.app_financeiro.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração de segurança da aplicação.
 *
 * Estado atual (temporário): CSRF desabilitado e todas as requisições liberadas (permitAll).
 * Isso permite desenvolvimento e testes locais sem autenticação.
 *
 * Fase 10 do roadmap substituirá esta configuração por:
 * - Filtro JWT para validar tokens em cada requisição
 * - Rotas publicas (/api/auth/**) liberadas sem token
 * - Demais rotas exigindo autenticacao via Bearer token
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Desabilita CSRF para facilitar testes locais por enquanto
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // Permite todas as requisições temporariamente (para não bloquear o envio do Frontend)
            );
        return http.build();
    }
}
