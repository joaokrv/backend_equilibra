package org.app_financeiro.backend.controller;

import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.dto.response.OtpStatusResponseDTO;
import org.app_financeiro.backend.mapper.UsuarioMapper;
import org.app_financeiro.backend.service.AutenticacaoService;
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

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes unitários do UsuarioController para o fluxo de verificação de OTP.
 * Mocka apenas AutenticacaoService (único dependency do fluxo verificarEmail).
 */
@ExtendWith(MockitoExtension.class)
class UsuarioControllerOtpTest {

    @Mock private AutenticacaoService autenticacaoService;
    @Mock private UsuarioService usuarioService;
    @Mock private RecuperacaoSenhaService recuperacaoSenhaService;
    @Mock private JwtService jwtService;
    @Mock private UsuarioMapper usuarioMapper;
    @Mock private UsuarioPendenteService usuarioPendenteService;

    @InjectMocks
    private UsuarioController usuarioController;

    private OtpStatusResponseDTO otpStatus(String status, int tentativas, String registroId, OffsetDateTime bloqueadoAte) {
        return new OtpStatusResponseDTO(
                status, tentativas,
                LocalDateTime.now().plusMinutes(15).atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                bloqueadoAte, null, null, registroId);
    }

    @Test
    void deveRetornarStatusOtpQuandoCodigoForInvalidoNoPreRegistro() {
        UUID registroId = UUID.randomUUID();
        var otpStatus = otpStatus("ATIVO", 4, registroId.toString(), null);

        when(autenticacaoService.verificarEmail(any()))
                .thenReturn(new AutenticacaoService.VerificarEmailResultado.CodigoInvalido(otpStatus));

        var response = usuarioController.verificarEmail(
                new VerificarEmailRequestDTO("otp-teste@email.com", "000000", registroId.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isInstanceOf(OtpStatusResponseDTO.class);
        assertThat((OtpStatusResponseDTO) response.getBody())
                .extracting(OtpStatusResponseDTO::status, OtpStatusResponseDTO::tentativasRestantes, OtpStatusResponseDTO::registroId)
                .containsExactly("ATIVO", 4, registroId.toString());
    }

    @Test
    void deveRetornarBloqueioQuandoOtpJaEstiverBloqueado() {
        UUID registroId = UUID.randomUUID();
        var bloqueadoAte = LocalDateTime.now().plusMinutes(30).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        var otpStatus = otpStatus("BLOQUEADO", 0, registroId.toString(), bloqueadoAte);

        when(autenticacaoService.verificarEmail(any()))
                .thenReturn(new AutenticacaoService.VerificarEmailResultado.Bloqueado(otpStatus));

        var response = usuarioController.verificarEmail(
                new VerificarEmailRequestDTO("otp-bloqueado@email.com", "000000", registroId.toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.LOCKED);
        assertThat(response.getBody()).isInstanceOf(OtpStatusResponseDTO.class);
        assertThat((OtpStatusResponseDTO) response.getBody())
                .extracting(OtpStatusResponseDTO::status, OtpStatusResponseDTO::tentativasRestantes, OtpStatusResponseDTO::registroId)
                .containsExactly("BLOQUEADO", 0, registroId.toString());
    }
}
