package org.app_financeiro.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.app_financeiro.backend.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/** Valida JWT de cada request e seta autenticação no SecurityContext. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Permite ler o token do header {@code Authorization: Bearer} além do cookie.
     * Em produção fica {@code false} (sessão apenas via cookie httpOnly); habilitado
     * em dev/test para a suíte de integração injetar o token diretamente.
     */
    @Value("${jwt.allow-header-auth:false}")
    private boolean allowHeaderAuth;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String jwt = null;

        // 1️⃣ Tentar ler token do cookie "accessToken" (prioridade)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            jwt = Arrays.stream(cookies)
                    .filter(c -> "accessToken".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        // 2️⃣ Fallback: header "Authorization: Bearer ..." — só em dev/test (allowHeaderAuth).
        // Em prod, sessão é exclusivamente via cookie httpOnly.
        if ((jwt == null || jwt.isBlank()) && allowHeaderAuth) {
            final String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
            }
        }

        if (jwt == null || jwt.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        final String userEmail;

        try {
            userEmail = jwtService.extractUsername(jwt);
        } catch (JwtException e) {
            log.debug("Token JWT inválido: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    String tokenChaveSessao = jwtService.extractChaveSessao(jwt);
                    if (userDetails instanceof UsuarioEntity usuario) {
                        // Rejeita tokens sem chaveSessao (pré-sessão) OU com chaveSessao diferente da atual.
                        // Condição anterior (tokenChaveSessao != null && ...) permitia bypass
                        // com tokens antigos que não carregavam chaveSessao na claim.
                        if (tokenChaveSessao == null || !tokenChaveSessao.equals(usuario.getChaveSessao())) {
                            log.warn("[SECURITY] Sessão inválida ou revogada para {}.", userEmail);
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ex) {
            log.error("Erro inesperado durante validação do token JWT para usuário '{}': {}", userEmail, ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
