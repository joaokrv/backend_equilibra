package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoCodigoVerificacao;
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

/**
 * Testes unitários do serviço de verificação de e-mail via OTP.
 * Cobre os cenários de geração, validação e expiração do código de 6 dígitos.
 */
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
        // O 3º argumento (registroId) é null para o fluxo legado
        VerificarEmailRequestDTO dto = new VerificarEmailRequestDTO(email, codigo, null);

        CodigoVerificacaoEntity entity = new CodigoVerificacaoEntity();
        entity.setEmail(email);
        entity.setCodigo(codigo);
        entity.setDataExpiracao(LocalDateTime.now().plusMinutes(10));
        entity.setTipo(TipoCodigoVerificacao.VERIFICACAO_EMAIL);
        entity.setUtilizado(false);

        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setEmail(email);
        usuario.setEmailVerificado(false);

        when(codigoVerificacaoRepository.findTopByEmailAndTipoAndIsUtilizadoFalseOrderByDataCriacaoDesc(email, TipoCodigoVerificacao.VERIFICACAO_EMAIL))
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
        VerificarEmailRequestDTO dto = new VerificarEmailRequestDTO(email, codigo, null);

        CodigoVerificacaoEntity entity = new CodigoVerificacaoEntity();
        entity.setDataExpiracao(LocalDateTime.now().minusMinutes(1)); // Expirado
        entity.setTipo(TipoCodigoVerificacao.VERIFICACAO_EMAIL);
        entity.setUtilizado(false);

        when(codigoVerificacaoRepository.findTopByEmailAndTipoAndIsUtilizadoFalseOrderByDataCriacaoDesc(email, TipoCodigoVerificacao.VERIFICACAO_EMAIL))
                .thenReturn(Optional.of(entity));

        // Act & Assert
        assertThatThrownBy(() -> emailVerificacaoService.verificarEmail(dto))
                .isInstanceOf(CodigoVerificacaoInvalidoException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    void deveLancarExceptionSeCodigoInexistente() {
        // Arrange — código ativo existe mas com código diferente → "inválido"
        VerificarEmailRequestDTO dto = new VerificarEmailRequestDTO("test@email.com", "000000", null);
        CodigoVerificacaoEntity entity = new CodigoVerificacaoEntity();
        entity.setCodigo("999999");
        entity.setDataExpiracao(LocalDateTime.now().plusMinutes(10));
        entity.setTipo(TipoCodigoVerificacao.VERIFICACAO_EMAIL);
        entity.setUtilizado(false);

        when(codigoVerificacaoRepository.findTopByEmailAndTipoAndIsUtilizadoFalseOrderByDataCriacaoDesc(any(), eq(TipoCodigoVerificacao.VERIFICACAO_EMAIL)))
                .thenReturn(Optional.of(entity));

        // Act & Assert
        assertThatThrownBy(() -> emailVerificacaoService.verificarEmail(dto))
                .isInstanceOf(CodigoVerificacaoInvalidoException.class)
                .hasMessageContaining("inválido");
    }

    /**
     * Testa a validação simples de código (usada no fluxo de pré-registro).
     * O método 'validarCodigoSimples' não toca na entidade de Usuário.
     */
    @Test
    void deveValidarCodigoSimplesComSucesso() {
        // Arrange
        String email = "preregistro@email.com";
        String codigo = "654321";

        CodigoVerificacaoEntity entity = new CodigoVerificacaoEntity();
        entity.setEmail(email);
        entity.setCodigo(codigo);
        entity.setDataExpiracao(LocalDateTime.now().plusMinutes(10));
        entity.setTipo(TipoCodigoVerificacao.VERIFICACAO_EMAIL);
        entity.setUtilizado(false);

        when(codigoVerificacaoRepository.findTopByEmailAndTipoAndIsUtilizadoFalseOrderByDataCriacaoDesc(email, TipoCodigoVerificacao.VERIFICACAO_EMAIL))
                .thenReturn(Optional.of(entity));

        // Act
        emailVerificacaoService.validarCodigoSimples(email, codigo, TipoCodigoVerificacao.VERIFICACAO_EMAIL);

        // Assert — OTP marcado como utilizado, mas sem tocar em UsuarioRepository
        assertThat(entity.isUtilizado()).isTrue();
        verify(codigoVerificacaoRepository).save(entity);
        verifyNoInteractions(usuarioRepository);
    }
}
