package org.app_financeiro.backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.ReativarContaRequestDTO;
import org.app_financeiro.backend.dto.request.ReenviarCodigoRequestDTO;
import org.app_financeiro.backend.dto.request.ResetarSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarAcaoContaRequestDTO;
import org.app_financeiro.backend.dto.request.ConfirmarAcaoContaRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarRecuperacaoSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.dto.response.AuthResponseDTO;
import org.app_financeiro.backend.dto.response.OtpStatusResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.CredenciaisInvalidasException;
import org.app_financeiro.backend.mapper.UsuarioMapper;
import org.app_financeiro.backend.service.AutenticacaoService;
import org.app_financeiro.backend.service.JwtService;
import org.app_financeiro.backend.service.RecuperacaoSenhaService;
import org.app_financeiro.backend.service.UsuarioPendenteService;
import org.app_financeiro.backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticacao", description = "Fluxos de registro, login e verificação de conta")
public class UsuarioController {

    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);

    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${cookie.same-site:None}")
    private String cookieSameSite;

    private final AutenticacaoService autenticacaoService;
    private final UsuarioService usuarioService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final JwtService jwtService;
    private final UsuarioMapper usuarioMapper;
    private final UsuarioPendenteService usuarioPendenteService;

    public UsuarioController(AutenticacaoService autenticacaoService,
                             UsuarioService usuarioService,
                             RecuperacaoSenhaService recuperacaoSenhaService,
                             JwtService jwtService,
                             UsuarioMapper usuarioMapper,
                             org.app_financeiro.backend.service.UsuarioPendenteService usuarioPendenteService) {
        this.autenticacaoService = autenticacaoService;
        this.usuarioService = usuarioService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.jwtService = jwtService;
        this.usuarioMapper = usuarioMapper;
        this.usuarioPendenteService = usuarioPendenteService;
    }

    @PostMapping("/pre-registrar")
    @Operation(summary = "Solicitar pré-registro")
    public ResponseEntity<OtpStatusResponseDTO> preRegistrar(@Valid @RequestBody UsuarioRegistroRequestDTO dto) {
        var resultado = autenticacaoService.preRegistrar(dto);
        return ResponseEntity.status(resultado.httpStatus()).body(resultado.otpStatus());
    }

    @GetMapping("/otp-status")
    @Operation(summary = "Consultar status do OTP")
    public ResponseEntity<OtpStatusResponseDTO> getOtpStatus(@RequestParam String registroId) {
        var pendente = usuarioPendenteService.buscarOuFalhar(java.util.UUID.fromString(registroId));
        return ResponseEntity.ok(usuarioPendenteService.mapearParaStatus(pendente));
    }

    @PostMapping("/login")
    @Operation(summary = "Login de usuário")
    public ResponseEntity<?> login(@Valid @RequestBody UsuarioLoginRequestDTO dto,
                                   HttpServletResponse httpResponse) {
        var resultado = autenticacaoService.login(dto);
        return switch (resultado) {
            case AutenticacaoService.LoginResultado.Bloqueado b ->
                ResponseEntity.status(423).body(Map.of(
                    "erro", "Conta bloqueada.",
                    "desbloqueio_em_segundos", b.segundosRestantes()));

            case AutenticacaoService.LoginResultado.EmailNaoVerificado nv ->
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    new AuthResponseDTO(null, null, 0, null, nv.otpStatus()));

            case AutenticacaoService.LoginResultado.ContaDesativada cd ->
                ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "codigo", "CONTA_DESATIVADA",
                    "mensagem", "Sua conta está desativada. Enviamos um código para seu e-mail para reativá-la.",
                    "email", cd.emailMascarado()));

            case AutenticacaoService.LoginResultado.CredenciaisInvalidas ignored ->
                throw new BadCredentialsException("Credenciais inválidas");

            case AutenticacaoService.LoginResultado.Sucesso s -> {
                setAccessTokenCookie(httpResponse, s.accessToken());
                setRefreshTokenCookie(httpResponse, s.refreshToken());
                yield ResponseEntity.ok(new AuthResponseDTO(
                    null, null,
                    jwtService.getAccessTokenExpiration(),
                    usuarioMapper.toResponse(s.usuario()),
                    null));
            }
        };
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar token")
    public ResponseEntity<AuthResponseDTO> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse httpResponse) {

        var resultado = autenticacaoService.refresh(refreshToken);
        if (resultado.invalido()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (resultado.reuso()) {
            clearRefreshTokenCookie(httpResponse);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        setAccessTokenCookie(httpResponse, resultado.accessToken());
        setRefreshTokenCookie(httpResponse, resultado.refreshToken());
        return ResponseEntity.ok(new AuthResponseDTO(
            null, null,
            jwtService.getAccessTokenExpiration(),
            usuarioMapper.toResponse(resultado.usuario()),
            null));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        autenticacaoService.logout(refreshToken);
        clearAccessTokenCookie(httpResponse);
        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verificar-email")
    @Operation(summary = "Verificar e-mail")
    public ResponseEntity<?> verificarEmail(@Valid @RequestBody VerificarEmailRequestDTO dto) {
        var resultado = autenticacaoService.verificarEmail(dto);
        return switch (resultado) {
            case AutenticacaoService.VerificarEmailResultado.Bloqueado b ->
                ResponseEntity.status(HttpStatus.LOCKED).body(b.otpStatus());
            case AutenticacaoService.VerificarEmailResultado.CodigoInvalido ci ->
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ci.otpStatus());
            case AutenticacaoService.VerificarEmailResultado.Sucesso s ->
                ResponseEntity.ok(Map.of("mensagem", "E-mail verificado com sucesso!", "email", s.email()));
            case AutenticacaoService.VerificarEmailResultado.SucessoLegado ignored ->
                ResponseEntity.ok("E-mail verificado com sucesso!");
        };
    }

    @PostMapping("/reenviar-codigo")
    @Operation(summary = "Reenviar código")
    public ResponseEntity<?> reenviarCodigo(@Valid @RequestBody ReenviarCodigoRequestDTO dto) {
        var resultado = autenticacaoService.reenviarCodigo(dto);
        if (resultado.legado()) return ResponseEntity.ok("Novo código de verificação enviado!");
        return ResponseEntity.status(resultado.httpStatus()).body(resultado.otpStatus());
    }

    @PostMapping("/solicitar-acao-conta")
    @Operation(summary = "Solicitar ação de conta")
    public ResponseEntity<?> solicitarAcaoConta(@Valid @RequestBody SolicitarAcaoContaRequestDTO dto,
                                                @AuthenticationPrincipal UsuarioEntity usuario) {
        if (usuario == null) throw new CredenciaisInvalidasException();
        var cooldown = autenticacaoService.solicitarAcaoConta(dto, usuario);
        if (cooldown.isPresent()) {
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(cooldown.get().segundosRestantes()))
                    .body(Map.of("mensagem", "Aguarde para solicitar novo código."));
        }
        return ResponseEntity.ok(Map.of("mensagem", "Código de confirmação enviado."));
    }

    @DeleteMapping("/excluir-conta")
    @Operation(summary = "Excluir conta")
    public ResponseEntity<?> excluirConta(@Valid @RequestBody ConfirmarAcaoContaRequestDTO dto,
                                          @AuthenticationPrincipal UsuarioEntity usuario) {
        if (usuario == null) throw new CredenciaisInvalidasException();
        usuarioService.excluirConta(dto, usuario);
        return ResponseEntity.ok(Map.of("mensagem", "Conta excluída com sucesso."));
    }

    @PostMapping("/desativar-conta")
    @Operation(summary = "Desativar conta")
    public ResponseEntity<?> desativarConta(@Valid @RequestBody ConfirmarAcaoContaRequestDTO dto,
                                            @AuthenticationPrincipal UsuarioEntity usuario) {
        if (usuario == null) throw new CredenciaisInvalidasException();
        usuarioService.desativarConta(dto, usuario);
        return ResponseEntity.ok(Map.of("mensagem", "Conta desativada com sucesso."));
    }

    @PostMapping("/reativar-conta")
    @Operation(summary = "Reativar conta")
    public ResponseEntity<String> reativarConta(@Valid @RequestBody ReativarContaRequestDTO dto) {
        usuarioService.reativarConta(dto.email(), dto.senha(), dto.codigo());
        return ResponseEntity.ok("Conta reativada com sucesso! Agora você pode fazer login.");
    }

    @PostMapping("/solicitar-recuperacao")
    @Operation(summary = "Solicitar recuperação de senha")
    public ResponseEntity<String> solicitarRecuperacao(@Valid @RequestBody SolicitarRecuperacaoSenhaRequestDTO dto) {
        long inicio = System.currentTimeMillis();
        try {
            recuperacaoSenhaService.solicitarRecuperacao(dto);
        } finally {
            long restante = 1200 - (System.currentTimeMillis() - inicio);
            if (restante > 0) try { Thread.sleep(restante); } catch (InterruptedException ignored) {}
        }
        return ResponseEntity.ok("Se o e-mail estiver cadastrado, você receberá um link de recuperação.");
    }

    @GetMapping("/validar-token")
    @Operation(summary = "Validar token de recuperação")
    public ResponseEntity<Map<String, String>> validarToken(@RequestParam String token) {
        String email = recuperacaoSenhaService.validarToken(token);
        return ResponseEntity.ok(Map.of("email", email));
    }

    @PostMapping("/resetar-senha")
    @Operation(summary = "Resetar senha")
    public ResponseEntity<String> resetarSenha(@Valid @RequestBody ResetarSenhaRequestDTO dto) {
        recuperacaoSenhaService.resetarSenha(dto);
        return ResponseEntity.ok("Senha redefinida com sucesso! Faça login com sua nova senha.");
    }

    // ── Cookies ──────────────────────────────────────────────────────────────

    private void setAccessTokenCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("accessToken", token)
                .httpOnly(true).secure(cookieSecure).path("/")
                .maxAge(jwtService.getAccessTokenExpiration() / 1000).sameSite(cookieSameSite)
                .build().toString());
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("refreshToken", token)
                .httpOnly(true).secure(cookieSecure).path("/api/auth")
                .maxAge(jwtService.getRefreshTokenExpiration() / 1000).sameSite(cookieSameSite)
                .build().toString());
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("accessToken", "")
                .httpOnly(true).secure(cookieSecure).path("/").maxAge(0).sameSite(cookieSameSite)
                .build().toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("refreshToken", "")
                .httpOnly(true).secure(cookieSecure).path("/api/auth").maxAge(0).sameSite(cookieSameSite)
                .build().toString());
    }
}
