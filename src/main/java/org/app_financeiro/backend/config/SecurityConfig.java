package org.app_financeiro.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/** Segurança JWT stateless: filtro, CORS, CSRF off, rotas públicas e protegidas. */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final JwtAuthenticationEntryPoint jwtAuthEntryPoint;

    private static final String[] WHITE_LIST_URL = {
            "/api/auth/login",
            "/api/auth/registrar",
            "/api/auth/pre-registrar",
            "/api/auth/verificar-email",
            "/api/auth/reenviar-codigo",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/solicitar-recuperacao",
            "/api/auth/resetar-senha",
            "/api/auth/reativar-conta",
            "/api/auth/otp-status",
            "/api/auth/validar-token",
            "/actuator/health",
            "/v2/api-docs",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/api-docs",
            "/api-docs/**",
            "/swagger-resources",
            "/swagger-resources/**",
            "/configuration/ui",
            "/configuration/security",
            "/swagger-ui/**",
            "/webjars/**",
            "/swagger-ui.html",
            "/internal/jobs/**"
    };


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> {
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"));
                    headers.frameOptions(frame -> frame.deny());
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .maxAgeInSeconds(63072000)
                            .includeSubDomains(true)
                            .preload(true)
                    );
                })
                .authorizeHttpRequests(req ->
                        req.requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                                .requestMatchers(WHITE_LIST_URL)
                                .permitAll()
                                .requestMatchers("/actuator/**").hasRole("ADMIN")
                                .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Primary
    @Bean
    @Profile("!test")
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty() || origins.contains("*")) {
            throw new IllegalStateException(
                "CORS_ALLOWED_ORIGINS inválido: lista vazia ou contém '*'. " +
                "Defina origens explícitas, sem wildcard, pois allowCredentials=true.");
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", "X-Requested-With"));
                configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
