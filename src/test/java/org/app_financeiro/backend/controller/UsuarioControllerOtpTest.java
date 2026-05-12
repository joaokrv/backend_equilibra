package org.app_financeiro.backend.controller;

import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.dto.response.OtpStatusResponseDTO;
import org.app_financeiro.backend.entity.UsuarioPendenteEntity;
import org.app_financeiro.backend.enums.TipoCodigoVerificacao;
import org.app_financeiro.backend.exception.CodigoVerificacaoInvalidoException;
import org.app_financeiro.backend.mapper.UsuarioMapper;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.app_financeiro.backend.service.EmailVerificacaoService;
import org.app_financeiro.backend.service.JwtService;
import org.app_financeiro.backend.service.RecuperacaoSenhaService;
import org.app_financeiro.backend.service.UsuarioPendenteService;
import org.app_financeiro.backend.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerOtpTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private EmailVerificacaoService emailVerificacaoService;

    @Mock
    private RecuperacaoSenhaService recuperacaoSenhaService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UsuarioPendenteService usuarioPendenteService;

    @Mock
    private UsuarioPendenteRepository usuarioPendenteRepository;

    @Mock
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Mock
    private UsuarioMapper usuarioMapper;

    @InjectMocks
    private UsuarioController usuarioController;

    @Test
    void deveRetornarStatusOtpQuandoCodigoForInvalidoNoPreRegistro() {
        UUID registroId = UUID.randomUUID();
        String email = "otp-teste@email.com";
        String codigo = "000000";

        UsuarioPendenteEntity pendente = new UsuarioPendenteEntity();
        pendente.setId(registroId);
        pendente.setEmail(email);
        pendente.setNome("Usuario Teste");
        pendente.setSenhaHash("hash");
        pendente.setExpiraEm(LocalDateTime.now().plusMinutes(15));

        when(usuarioPendenteService.buscarOuFalharComLock(registroId)).thenReturn(pendente);
        doThrow(new CodigoVerificacaoInvalidoException("Código inválido."))
                .when(emailVerificacaoService)
                .validarCodigoSimples(eq(email), eq(codigo), eq(TipoCodigoVerificacao.VERIFICACAO_EMAIL));
        when(usuarioPendenteService.mapearParaStatus(pendente)).thenReturn(
                new OtpStatusResponseDTO(
                        "ATIVO",
                        4,
                        pendente.getExpiraEm().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                        null,
                        null,
                        null,
                        registroId.toString()
                )
        );

        var response = usuarioController.verificarEmail(new VerificarEmailRequestDTO(email, codigo, registroId.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isInstanceOf(OtpStatusResponseDTO.class);
        assertThat((OtpStatusResponseDTO) response.getBody())
                .extracting(OtpStatusResponseDTO::status, OtpStatusResponseDTO::tentativasRestantes, OtpStatusResponseDTO::registroId)
                .containsExactly("ATIVO", 4, registroId.toString());

        verify(usuarioPendenteService).registrarTentativaFalha(pendente);
    }

        @Test
        void deveRetornarBloqueioQuandoOtpJaEstiverBloqueado() {
                UUID registroId = UUID.randomUUID();
                String email = "otp-bloqueado@email.com";

                UsuarioPendenteEntity pendente = new UsuarioPendenteEntity();
                pendente.setId(registroId);
                pendente.setEmail(email);
                pendente.setNome("Usuario Bloqueado");
                pendente.setSenhaHash("hash");
                pendente.setExpiraEm(LocalDateTime.now().plusMinutes(15));
                pendente.setBloqueadoAte(LocalDateTime.now().plusMinutes(30));
                pendente.setTentativasFalhas(5);

                when(usuarioPendenteService.buscarOuFalharComLock(registroId)).thenReturn(pendente);
                when(usuarioPendenteService.mapearParaStatus(pendente)).thenReturn(
                                new OtpStatusResponseDTO(
                                                "BLOQUEADO",
                                                0,
                                                pendente.getExpiraEm().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                                                pendente.getBloqueadoAte().atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                                                null,
                                                null,
                                                registroId.toString()
                                )
                );

                var response = usuarioController.verificarEmail(new VerificarEmailRequestDTO(email, "000000", registroId.toString()));

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
                assertThat(response.getHeaders().getFirst("Retry-After")).isNotBlank();
                assertThat(response.getBody()).isInstanceOf(OtpStatusResponseDTO.class);
                assertThat((OtpStatusResponseDTO) response.getBody())
                                .extracting(OtpStatusResponseDTO::status, OtpStatusResponseDTO::tentativasRestantes, OtpStatusResponseDTO::registroId)
                                .containsExactly("BLOQUEADO", 0, registroId.toString());

                verify(usuarioPendenteService, never()).registrarTentativaFalha(pendente);
                verifyNoInteractions(emailVerificacaoService, usuarioService);
        }
}
