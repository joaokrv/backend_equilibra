package org.app_financeiro.backend.config;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.app_financeiro.backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do {@link JwtAuthenticationFilter}.
 *
 * <p>Verifica o comportamento do filtro JWT em três cenários:
 * sem token, token inválido e token válido.</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Test
    void deveSeguirSemAutenticarQuandoNaoHaToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void deveSeguirSemAutenticarQuandoTokenInvalido() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-invalido");
        when(jwtService.extractUsername("token-invalido")).thenThrow(new JwtException("Token malformado"));

        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void deveAutenticarQuandoTokenValido() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(jwtService.extractUsername("token-valido")).thenReturn("joao@email.com");

        UserDetails userDetails = new User("joao@email.com", "senha_hash", Collections.emptyList());
        when(userDetailsService.loadUserByUsername("joao@email.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("token-valido", userDetails)).thenReturn(true);

        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("joao@email.com");

        SecurityContextHolder.clearContext();
    }

    @Test
    void naoDeveAutenticarQuandoTokenExpirado() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-expirado");
        when(jwtService.extractUsername("token-expirado")).thenReturn("joao@email.com");

        UserDetails userDetails = new User("joao@email.com", "senha_hash", Collections.emptyList());
        when(userDetailsService.loadUserByUsername("joao@email.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("token-expirado", userDetails)).thenReturn(false);

        SecurityContextHolder.clearContext();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
