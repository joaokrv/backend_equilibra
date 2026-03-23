package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CredenciaisInvalidasException;
import org.app_financeiro.backend.exception.EmailJaCadastradoException;
import org.app_financeiro.backend.exception.EmailNaoVerificadoException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.mapper.UsuarioMapper;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do {@link UsuarioService}.
 *
 * <p>NOTA: O pepper NÃO é testado aqui. O {@link org.app_financeiro.backend.config.PepperedPasswordEncoder}
 * é um decorator transparente — o Service chama {@code passwordEncoder.encode(senha)} sem saber
 * do pepper. O pepper é testado em {@code PepperedPasswordEncoderTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioMapper usuarioMapper;

    @InjectMocks
    private UsuarioService usuarioService;

    // ─── Registro ──────────────────────────────────────────────

    @Test
    void deveRegistrarUsuarioComSucesso() {
        // Arrange
        UsuarioRegistroRequestDTO request = new UsuarioRegistroRequestDTO("Joao", "joao@email.com", "senha123");
        when(usuarioRepository.existsByEmailIncludingInactive("joao@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("senha_hash");

        when(usuarioRepository.save(any(UsuarioEntity.class))).thenAnswer(i -> {
            UsuarioEntity u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        UsuarioResponseDTO responseDTO = new UsuarioResponseDTO(1L, "Joao", "joao@email.com");
        when(usuarioMapper.toResponse(any())).thenReturn(responseDTO);

        // Act
        usuarioService.registrarUsuario(request);

        // Assert
        verify(passwordEncoder).encode("senha123");
        verify(usuarioRepository).save(any(UsuarioEntity.class));
    }

    @Test
    void deveLancarExceptionAoRegistrarEmailJaExistente() {
        // Arrange
        UsuarioRegistroRequestDTO request = new UsuarioRegistroRequestDTO("Joao", "joao@email.com", "senha123");
        when(usuarioRepository.existsByEmailIncludingInactive("joao@email.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> usuarioService.registrarUsuario(request))
                .isInstanceOf(EmailJaCadastradoException.class);
    }

    // ─── Login ─────────────────────────────────────────────────

    @Test
    void deveFazerLoginComSucesso() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");
        usuario.setEmailVerificado(true);

        when(usuarioRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha123", "senha_hash")).thenReturn(true);
        UsuarioResponseDTO responseDTO = new UsuarioResponseDTO(1L, "Joao", "joao@email.com");
        when(usuarioMapper.toResponse(usuario)).thenReturn(responseDTO);

        // Act
        UsuarioResponseDTO result = usuarioService.loginUsuario("joao@email.com", "senha123");

        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    void deveLancarExceptionNoLoginSeEmailNaoVerificado() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");
        usuario.setEmailVerificado(false);

        when(usuarioRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha123", "senha_hash")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> usuarioService.loginUsuario("joao@email.com", "senha123"))
                .isInstanceOf(EmailNaoVerificadoException.class);
    }

    @Test
    void deveLancarExceptionNoLoginSeSenhaInvalida() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");

        when(usuarioRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(anyString(), eq("senha_hash"))).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> usuarioService.loginUsuario("joao@email.com", "senha_errada"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    // ─── Desativar Conta ───────────────────────────────────────

    @Test
    void deveDesativarContaComSucesso() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setAtivo(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        // Act
        usuarioService.desativarConta(1L);

        // Assert
        assertThat(usuario.isAtivo()).isFalse();
        verify(usuarioRepository).save(usuario);
    }

    // ─── Reativar Conta ────────────────────────────────────────

    @Test
    void deveReativarContaComSenhaCorreta() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");
        usuario.setAtivo(false);

        when(usuarioRepository.findInactiveByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha123", "senha_hash")).thenReturn(true);

        // Act
        usuarioService.reativarConta("joao@email.com", "senha123");

        // Assert
        assertThat(usuario.isAtivo()).isTrue();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveLancarExceptionAoReativarComSenhaIncorreta() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");
        usuario.setAtivo(false);

        when(usuarioRepository.findInactiveByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha_errada", "senha_hash")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> usuarioService.reativarConta("joao@email.com", "senha_errada"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveLancarExceptionAoReativarContaInexistente() {
        // Arrange
        when(usuarioRepository.findInactiveByEmail("naoexiste@email.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> usuarioService.reativarConta("naoexiste@email.com", "senha123"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
