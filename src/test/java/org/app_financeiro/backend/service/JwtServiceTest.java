package org.app_financeiro.backend.service;

import io.jsonwebtoken.Jwts;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    // Chave de 256 bits (32 bytes) em Base64
    private static final String SECRET_KEY = Base64.getEncoder().encodeToString("test-secret-key-32-chars-long-!!!".getBytes());
    private static final long EXPIRATION = 3600000; // 1 hora

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", EXPIRATION);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", EXPIRATION * 24);
    }

    @Test
    void deveGerarTokenValido() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(123L);
        usuario.setEmail("test@email.com");

        // Act
        String token = jwtService.generateAccessToken(usuario);

        // Assert
        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("test@email.com");
        assertThat(jwtService.extractUsuarioId(token)).isEqualTo(123L);
    }

    @Test
    void deveValidarTokenCorretamente() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("test@email.com");
        String token = jwtService.generateAccessToken(usuario);

        // Act
        boolean isValid = jwtService.isTokenValid(token, usuario);

        // Assert
        assertThat(isValid).isTrue();
    }

    @Test
    void deveInvalidarTokenParaUsuarioDiferente() {
        // Arrange
        UsuarioEntity usuario1 = new UsuarioEntity();
        usuario1.setEmail("user1@email.com");
        
        UsuarioEntity usuario2 = new UsuarioEntity();
        usuario2.setEmail("user2@email.com");

        String token = jwtService.generateAccessToken(usuario1);

        // Act
        boolean isValid = jwtService.isTokenValid(token, usuario2);

        // Assert
        assertThat(isValid).isFalse();
    }
}
