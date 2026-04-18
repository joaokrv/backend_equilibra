package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.AlterarSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.enums.MoedaEnum;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CredenciaisInvalidasException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.mapper.UsuarioMapper;
import org.app_financeiro.backend.repository.CategoriaRepository;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
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

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private ContaRepository contaRepository;

    @Mock
    private InvestimentoRepository investimentoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

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

        // Act
        usuarioService.registrarUsuario(request);

        // Assert
        verify(passwordEncoder).encode("senha123");
        verify(usuarioRepository).save(any(UsuarioEntity.class));
    }

    @Test
    void deveCriarCategoriaPadraoInvestimentoComoDespesaAoRegistrarUsuario() {
        // Arrange
        UsuarioRegistroRequestDTO request = new UsuarioRegistroRequestDTO("Joao", "joao@email.com", "senha123");
        when(usuarioRepository.existsByEmailIncludingInactive("joao@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("senha_hash");

        when(usuarioRepository.save(any(UsuarioEntity.class))).thenAnswer(i -> {
            UsuarioEntity u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        // Act
        usuarioService.registrarUsuario(request);

        // Assert
        ArgumentCaptor<CategoriaEntity> categoriaCaptor = ArgumentCaptor.forClass(CategoriaEntity.class);
        verify(categoriaRepository, times(16)).save(categoriaCaptor.capture());

        List<CategoriaEntity> categoriasCriadas = categoriaCaptor.getAllValues();
        boolean possuiCategoriaInvestimentoDespesaPadrao = categoriasCriadas.stream()
                .anyMatch(categoria -> "Investimento".equals(categoria.getNome())
                        && categoria.getTipo() == TipoTransacao.DESPESA
                && categoria.isPadrao());

        assertThat(possuiCategoriaInvestimentoDespesaPadrao).isTrue();
    }

    @Test
    void deveRetornarFalseAoRegistrarEmailJaExistente() {
        // Anti-enumeração (B1-A2): email duplicado retorna false silenciosamente
        UsuarioRegistroRequestDTO request = new UsuarioRegistroRequestDTO("Joao", "joao@email.com", "senha123");
        when(usuarioRepository.existsByEmailIncludingInactive("joao@email.com")).thenReturn(true);

        boolean result = usuarioService.registrarUsuario(request);

        assertThat(result).isFalse();
        verify(usuarioRepository, never()).save(any());
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
        UsuarioResponseDTO responseDTO = new UsuarioResponseDTO(1L, "Joao", "joao@email.com", true, null, null, MoedaEnum.BRL);
        when(usuarioMapper.toResponse(usuario)).thenReturn(responseDTO);

        // Act
        UsuarioResponseDTO result = usuarioService.loginUsuario("joao@email.com", "senha123");

        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    void devePermitirLoginComEmailNaoVerificado() {
        // Arrange — login agora funciona mesmo sem email verificado
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");
        usuario.setEmailVerificado(false);

        when(usuarioRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha123", "senha_hash")).thenReturn(true);
        UsuarioResponseDTO responseDTO = new UsuarioResponseDTO(1L, "Joao",
                "joao@email.com", false, null, null, MoedaEnum.BRL);
        when(usuarioMapper.toResponse(usuario)).thenReturn(responseDTO);

        // Act
        UsuarioResponseDTO result = usuarioService.loginUsuario("joao@email.com", "senha123");

        // Assert — login bem-sucedido, verificação é tratada no frontend
        assertThat(result).isNotNull();
        assertThat(result.isEmailVerificado()).isFalse();
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

    // ─── Upload de Foto (Segurança Binária) ─────────────────────

    @Test
    void deveLancarExceptionAoFazerUploadDeArquivoComAssinaturaInvalida() {
        // Arrange: Um arquivo de texto fingindo ser PNG
        byte[] scriptMalicioso = "<?php echo 'Hacked'; ?>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", scriptMalicioso);
        
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        // Act & Assert: Deve falhar ao ler os Magic Bytes
        assertThatThrownBy(() -> usuarioService.atualizarFoto(1L, file))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Formato de arquivo inválido");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveFazerUploadDeFotoComAssinaturaValida() {
        // Arrange: Assinatura PNG real (Magic Bytes: 89 50 4E 47)
        byte[] pngValido = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", pngValido);
        
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        // Act
        usuarioService.atualizarFoto(1L, file);

        // Assert
        assertThat(usuario.getFoto()).isEqualTo(pngValido);
        verify(usuarioRepository).save(usuario);
    }

    // ─── Alterar Senha ─────────────────────────────────────────

    @Test
    void deveAlterarSenhaComSucesso() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setSenha("hash_atual");
        usuario.setChaveSessao("sessao-ativa");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaAtual1!", "hash_atual")).thenReturn(true);
        when(passwordEncoder.matches("NovaSenha1!", "hash_atual")).thenReturn(false);
        when(passwordEncoder.encode("NovaSenha1!")).thenReturn("hash_nova");

        // Act
        usuarioService.alterarSenha(1L, new AlterarSenhaRequestDTO("SenhaAtual1!", "NovaSenha1!"));

        // Assert
        assertThat(usuario.getSenha()).isEqualTo("hash_nova");
        assertThat(usuario.getChaveSessao()).isNull();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveLancarExceptionAoAlterarSenhaComSenhaAtualErrada() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setSenha("hash_atual");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaErrada1!", "hash_atual")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> usuarioService.alterarSenha(1L,
                new AlterarSenhaRequestDTO("SenhaErrada1!", "NovaSenha1!")))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveLancarExceptionAoAlterarSenhaParaMesmaSenha() {
        // Arrange
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setSenha("hash_atual");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("MesmaSenha1!", "hash_atual")).thenReturn(true);
        when(passwordEncoder.matches("MesmaSenha1!", "hash_atual")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> usuarioService.alterarSenha(1L,
                new AlterarSenhaRequestDTO("MesmaSenha1!", "MesmaSenha1!")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("diferente da atual");

        verify(usuarioRepository, never()).save(any());
    }
}
