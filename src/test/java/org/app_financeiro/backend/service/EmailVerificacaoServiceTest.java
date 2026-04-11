package org.app_financeiro.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CodigoVerificacaoInvalidoException;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificacaoServiceTest {

    @Mock
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailVerificacaoService emailVerificacaoService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void deveGerarCodigoESalvarNoBanco() {
        // Arrange
        String email = "test@email.com";
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        // Act
        emailVerificacaoService.gerarCodigo(email);
 
        // Assert
        verify(codigoVerificacaoRepository).save(any(CodigoVerificacaoEntity.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void deveVerificarEmailComSucesso() {
        // Arrange
        String email = "test@email.com";
        String codigo = "123456";
        VerificarEmailRequestDTO dto = new VerificarEmailRequestDTO(email, codigo);

        CodigoVerificacaoEntity entity = new CodigoVerificacaoEntity();
        entity.setEmail(email);
        entity.setCodigo(codigo);
        entity.setDataExpiracao(LocalDateTime.now().plusMinutes(10));
        entity.setUtilizado(false);

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail(email);
        usuario.setEmailVerificado(false);

        when(codigoVerificacaoRepository.findByEmailAndCodigoAndIsUtilizadoFalse(email, codigo))
                .thenReturn(Optional.of(entity));
        when(usuarioRepository.findByEmail(email)).thenReturn(Optional.of(usuario));

        // Act
        emailVerificacaoService.verificarEmail(dto);

        // Assert
        assertThat(entity.isUtilizado()).isTrue();
        assertThat(usuario.isEmailVerificado()).isTrue();
        verify(codigoVerificacaoRepository).save(entity);
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveLancarExceptionSeCodigoExpirado() {
        // Arrange
        String email = "test@email.com";
        String codigo = "123456";
        VerificarEmailRequestDTO dto = new VerificarEmailRequestDTO(email, codigo);

        CodigoVerificacaoEntity entity = new CodigoVerificacaoEntity();
        entity.setDataExpiracao(LocalDateTime.now().minusMinutes(1)); // Expirado
        entity.setUtilizado(false);

        when(codigoVerificacaoRepository.findByEmailAndCodigoAndIsUtilizadoFalse(email, codigo))
                .thenReturn(Optional.of(entity));

        // Act & Assert
        assertThatThrownBy(() -> emailVerificacaoService.verificarEmail(dto))
                .isInstanceOf(CodigoVerificacaoInvalidoException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    void deveLancarExceptionSeCodigoInexistente() {
        // Arrange
        VerificarEmailRequestDTO dto = new VerificarEmailRequestDTO("test@email.com", "000000");
        when(codigoVerificacaoRepository.findByEmailAndCodigoAndIsUtilizadoFalse(any(), any()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> emailVerificacaoService.verificarEmail(dto))
                .isInstanceOf(CodigoVerificacaoInvalidoException.class)
                .hasMessageContaining("inválido");
    }
}
