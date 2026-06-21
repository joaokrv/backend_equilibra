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

    @Mock
    private EmailVerificacaoService emailVerificacaoService;

    @InjectMocks
    private UsuarioService usuarioService;


    @Test
    void deveRegistrarUsuarioComSucesso() {
        UsuarioRegistroRequestDTO request = new UsuarioRegistroRequestDTO("Joao", "joao@email.com", "senha123");
        when(usuarioRepository.existsByEmailIncludingInactive("joao@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("senha_hash");

        when(usuarioRepository.save(any(UsuarioEntity.class))).thenAnswer(i -> {
            UsuarioEntity u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        usuarioService.registrarUsuario(request);

        verify(passwordEncoder).encode("senha123");
        verify(usuarioRepository).save(any(UsuarioEntity.class));
    }

    @Test
    void deveCriarCategoriaPadraoInvestimentoComoDespesaAoRegistrarUsuario() {
        UsuarioRegistroRequestDTO request = new UsuarioRegistroRequestDTO("Joao", "joao@email.com", "senha123");
        when(usuarioRepository.existsByEmailIncludingInactive("joao@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("senha_hash");

        when(usuarioRepository.save(any(UsuarioEntity.class))).thenAnswer(i -> {
            UsuarioEntity u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        usuarioService.registrarUsuario(request);

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
        UsuarioRegistroRequestDTO request = new UsuarioRegistroRequestDTO("Joao", "joao@email.com", "senha123");
        when(usuarioRepository.existsByEmailIncludingInactive("joao@email.com")).thenReturn(true);

        boolean result = usuarioService.registrarUsuario(request);

        assertThat(result).isFalse();
        verify(usuarioRepository, never()).save(any());
    }


    @Test
    void deveFazerLoginComSucesso() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");
        usuario.setEmailVerificado(true);

        when(usuarioRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha123", "senha_hash")).thenReturn(true);
        UsuarioResponseDTO responseDTO = new UsuarioResponseDTO(1L, "Joao", "joao@email.com", true, null, MoedaEnum.BRL, true);
        when(usuarioMapper.toResponse(usuario)).thenReturn(responseDTO);

        UsuarioResponseDTO result = usuarioService.loginUsuario("joao@email.com", "senha123");

        assertThat(result).isNotNull();
    }

    @Test
    void devePermitirLoginComEmailNaoVerificado() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");
        usuario.setEmailVerificado(false);

        when(usuarioRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha123", "senha_hash")).thenReturn(true);
        UsuarioResponseDTO responseDTO = new UsuarioResponseDTO(1L, "Joao",
                "joao@email.com", false, null, MoedaEnum.BRL, true);
        when(usuarioMapper.toResponse(usuario)).thenReturn(responseDTO);

        UsuarioResponseDTO result = usuarioService.loginUsuario("joao@email.com", "senha123");

        assertThat(result).isNotNull();
        assertThat(result.isEmailVerificado()).isFalse();
    }

    @Test
    void deveLancarExceptionNoLoginSeSenhaInvalida() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");

        when(usuarioRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(anyString(), eq("senha_hash"))).thenReturn(false);

        assertThatThrownBy(() -> usuarioService.loginUsuario("joao@email.com", "senha_errada"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }


    @Test
    void deveDesativarContaComSucesso() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setAtivo(true);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        usuarioService.desativarConta(1L);

        assertThat(usuario.isAtivo()).isFalse();
        verify(usuarioRepository).save(usuario);
    }


    @Test
    void deveReativarContaComSenhaCorreta() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");
        usuario.setAtivo(false);

        when(usuarioRepository.findInactiveByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha123", "senha_hash")).thenReturn(true);

        usuarioService.reativarConta("joao@email.com", "senha123", "123456");

        assertThat(usuario.isAtivo()).isTrue();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveLancarExceptionAoReativarComSenhaIncorreta() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail("joao@email.com");
        usuario.setSenha("senha_hash");
        usuario.setAtivo(false);

        when(usuarioRepository.findInactiveByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha_errada", "senha_hash")).thenReturn(false);

        assertThatThrownBy(() -> usuarioService.reativarConta("joao@email.com", "senha_errada", "123456"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveLancarExceptionAoReativarContaInexistente() {
        when(usuarioRepository.findInactiveByEmail("naoexiste@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.reativarConta("naoexiste@email.com", "senha123", "123456"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }


    @Test
    void deveAlterarSenhaComSucesso() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setSenha("hash_atual");
        usuario.setChaveSessao("sessao-ativa");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaAtual1!", "hash_atual")).thenReturn(true);
        when(passwordEncoder.matches("NovaSenha1!", "hash_atual")).thenReturn(false);
        when(passwordEncoder.encode("NovaSenha1!")).thenReturn("hash_nova");

        usuarioService.alterarSenha(1L, new AlterarSenhaRequestDTO("SenhaAtual1!", "NovaSenha1!"));

        assertThat(usuario.getSenha()).isEqualTo("hash_nova");
        assertThat(usuario.getChaveSessao()).isNull();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveLancarExceptionAoAlterarSenhaComSenhaAtualErrada() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setSenha("hash_atual");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("SenhaErrada1!", "hash_atual")).thenReturn(false);

        assertThatThrownBy(() -> usuarioService.alterarSenha(1L,
                new AlterarSenhaRequestDTO("SenhaErrada1!", "NovaSenha1!")))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void deveLancarExceptionAoAlterarSenhaParaMesmaSenha() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setSenha("hash_atual");

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("MesmaSenha1!", "hash_atual")).thenReturn(true);
        when(passwordEncoder.matches("MesmaSenha1!", "hash_atual")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.alterarSenha(1L,
                new AlterarSenhaRequestDTO("MesmaSenha1!", "MesmaSenha1!")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("diferente da atual");

        verify(usuarioRepository, never()).save(any());
    }
}
