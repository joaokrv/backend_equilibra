package org.app_financeiro.backend.service;

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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificacaoServiceTest {

    @Mock
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ExternalEmailSenderService externalEmailSenderService;

    @InjectMocks
    private EmailVerificacaoService emailVerificacaoService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void deveGerarCodigoESalvarNoBanco() {
        // Arrange
        String email = "test@email.com";

        // Act
        emailVerificacaoService.gerarCodigo(email);

        // Assert
        verify(codigoVerificacaoRepository).save(any(CodigoVerificacaoEntity.class));
        verify(externalEmailSenderService).sendHtml(eq(email), any(), any());
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

        when(codigoVerificacaoRepository.findTopByEmailAndIsUtilizadoFalseOrderByDataCriacaoDesc(email))
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

        when(codigoVerificacaoRepository.findTopByEmailAndIsUtilizadoFalseOrderByDataCriacaoDesc(email))
                .thenReturn(Optional.of(entity));

        // Act & Assert
        assertThatThrownBy(() -> emailVerificacaoService.verificarEmail(dto))
                .isInstanceOf(CodigoVerificacaoInvalidoException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    void deveLancarExceptionSeCodigoInexistente() {
        // Arrange — código ativo existe mas com código diferente → "inválido"
        VerificarEmailRequestDTO dto = new VerificarEmailRequestDTO("test@email.com", "000000");
        CodigoVerificacaoEntity entity = new CodigoVerificacaoEntity();
        entity.setCodigo("999999");
        entity.setDataExpiracao(LocalDateTime.now().plusMinutes(10));
        entity.setUtilizado(false);

        when(codigoVerificacaoRepository.findTopByEmailAndIsUtilizadoFalseOrderByDataCriacaoDesc(any()))
                .thenReturn(Optional.of(entity));

        // Act & Assert
        assertThatThrownBy(() -> emailVerificacaoService.verificarEmail(dto))
                .isInstanceOf(CodigoVerificacaoInvalidoException.class)
                .hasMessageContaining("inválido");
    }
}
