package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ConfirmarAlteracaoEmailRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarAlteracaoEmailRequestDTO;
import org.app_financeiro.backend.entity.SolicitacaoAlteracaoEmailEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CodigoVerificacaoInvalidoException;
import org.app_financeiro.backend.exception.CredenciaisInvalidasException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.SolicitacaoAlteracaoEmailRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlteracaoEmailServiceTest {

    @Mock
    private SolicitacaoAlteracaoEmailRepository solicitacaoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ExternalEmailSenderService externalEmailSenderService;

    @InjectMocks
    private AlteracaoEmailService alteracaoEmailService;

    private UsuarioEntity usuario;

    @BeforeEach
    void setUp() {
        usuario = new UsuarioEntity();
        usuario.setId(1L);
        usuario.setEmail("antigo@email.com");
        usuario.setSenha("hash_senha");
        usuario.setEmailVerificado(true);
        usuario.setChaveSessao("sessao-ativa");
    }

    // ─── Solicitar Alteração ───────────────────────────────────

    @Test
    void deveSolicitarAlteracaoDeEmailComSucesso() {
        // Arrange
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha1!", "hash_senha")).thenReturn(true);
        when(usuarioRepository.existsByEmailIncludingInactive("novo@email.com")).thenReturn(false);

        // Act
        alteracaoEmailService.solicitarAlteracao(1L,
                new SolicitarAlteracaoEmailRequestDTO("novo@email.com", "Senha1!"));

        // Assert
        verify(solicitacaoRepository).invalidarSolicitacoesAnteriores(1L);

        ArgumentCaptor<SolicitacaoAlteracaoEmailEntity> captor = ArgumentCaptor.forClass(SolicitacaoAlteracaoEmailEntity.class);
        verify(solicitacaoRepository).save(captor.capture());

        SolicitacaoAlteracaoEmailEntity salva = captor.getValue();
        assertThat(salva.getNovoEmail()).isEqualTo("novo@email.com");
        assertThat(salva.getCodigo()).hasSize(6);
        assertThat(salva.getUsuarioId()).isEqualTo(1L);
    }

    @Test
    void deveLancarExceptionAoSolicitarComSenhaErrada() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Errada!", "hash_senha")).thenReturn(false);

        assertThatThrownBy(() -> alteracaoEmailService.solicitarAlteracao(1L,
                new SolicitarAlteracaoEmailRequestDTO("novo@email.com", "Errada!")))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    void deveLancarExceptionAoSolicitarComMesmoEmail() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha1!", "hash_senha")).thenReturn(true);

        assertThatThrownBy(() -> alteracaoEmailService.solicitarAlteracao(1L,
                new SolicitarAlteracaoEmailRequestDTO("antigo@email.com", "Senha1!")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("diferente do atual");
    }

    @Test
    void deveLancarExceptionAoSolicitarComEmailJaEmUso() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Senha1!", "hash_senha")).thenReturn(true);
        when(usuarioRepository.existsByEmailIncludingInactive("ocupado@email.com")).thenReturn(true);

        assertThatThrownBy(() -> alteracaoEmailService.solicitarAlteracao(1L,
                new SolicitarAlteracaoEmailRequestDTO("ocupado@email.com", "Senha1!")))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("já está vinculado");
    }

    // ─── Confirmar Alteração ───────────────────────────────────

    @Test
    void deveConfirmarAlteracaoDeEmailComSucesso() {
        // Arrange
        SolicitacaoAlteracaoEmailEntity solicitacao = new SolicitacaoAlteracaoEmailEntity();
        solicitacao.setUsuarioId(1L);
        solicitacao.setNovoEmail("novo@email.com");
        solicitacao.setCodigo("123456");
        solicitacao.setDataExpiracao(LocalDateTime.now().plusMinutes(10));

        when(solicitacaoRepository.findTopByUsuarioIdAndIsUtilizadoFalseOrderByDataCriacaoDesc(1L))
                .thenReturn(Optional.of(solicitacao));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

        // Act
        alteracaoEmailService.confirmarAlteracao(1L, new ConfirmarAlteracaoEmailRequestDTO("123456"));

        // Assert
        assertThat(usuario.getEmail()).isEqualTo("novo@email.com");
        assertThat(usuario.isEmailVerificado()).isTrue();
        assertThat(usuario.getChaveSessao()).isNull();
        assertThat(solicitacao.isUtilizado()).isTrue();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void deveLancarExceptionAoConfirmarComCodigoInvalido() {
        SolicitacaoAlteracaoEmailEntity solicitacao = new SolicitacaoAlteracaoEmailEntity();
        solicitacao.setCodigo("999999");
        solicitacao.setDataExpiracao(LocalDateTime.now().plusMinutes(10));

        when(solicitacaoRepository.findTopByUsuarioIdAndIsUtilizadoFalseOrderByDataCriacaoDesc(1L))
                .thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> alteracaoEmailService.confirmarAlteracao(1L,
                new ConfirmarAlteracaoEmailRequestDTO("000000")))
                .isInstanceOf(CodigoVerificacaoInvalidoException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    void deveLancarExceptionAoConfirmarComCodigoExpirado() {
        SolicitacaoAlteracaoEmailEntity solicitacao = new SolicitacaoAlteracaoEmailEntity();
        solicitacao.setCodigo("123456");
        solicitacao.setDataExpiracao(LocalDateTime.now().minusMinutes(1));

        when(solicitacaoRepository.findTopByUsuarioIdAndIsUtilizadoFalseOrderByDataCriacaoDesc(1L))
                .thenReturn(Optional.of(solicitacao));

        assertThatThrownBy(() -> alteracaoEmailService.confirmarAlteracao(1L,
                new ConfirmarAlteracaoEmailRequestDTO("123456")))
                .isInstanceOf(CodigoVerificacaoInvalidoException.class)
                .hasMessageContaining("expirado");
    }
}
