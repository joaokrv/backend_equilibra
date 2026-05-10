package org.app_financeiro.backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.ReativarContaRequestDTO;
import org.app_financeiro.backend.dto.request.ReenviarCodigoRequestDTO;
import org.app_financeiro.backend.dto.request.ResetarSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarRecuperacaoSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.dto.response.AuthResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.app_financeiro.backend.service.EmailVerificacaoService;
import org.app_financeiro.backend.service.JwtService;
import org.app_financeiro.backend.service.RecuperacaoSenhaService;
import org.app_financeiro.backend.service.UsuarioService;
import org.app_financeiro.backend.service.UsuarioPendenteService;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
import org.app_financeiro.backend.entity.UsuarioPendenteEntity;
import org.app_financeiro.backend.dto.response.OtpStatusResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.app_financeiro.backend.mapper.UsuarioMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Controller responsável pelos endpoints de autenticação e verificação de e-mail.
 *
 * Endpoints:
 * - POST /api/auth/pre-registrar    → Inicia pré-registro com OTP obrigatório
 * - GET  /api/auth/otp-status       → Consulta estado do pré-registro
 * - POST /api/auth/login            → Autentica usuário e retorna access + refresh tokens
 * - POST /api/auth/refresh          → Renova o access token usando o refresh token
 * - POST /api/auth/verificar-email  → Valida código de 6 dígitos (pré-registro ou legado)
 * - POST /api/auth/reenviar-codigo  → Gera e reenvia novo código de verificação
 * - POST /api/auth/solicitar-recuperacao → Envia link de recuperação de senha por e-mail
 * - GET  /api/auth/validar-token    → Valida se o token de recuperação é válido
 * - POST /api/auth/resetar-senha    → Reseta a senha usando o token de recuperação
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticacao", description = "Fluxos de registro, login e verificação de conta")
public class UsuarioController {

    private static final Logger log = LoggerFactory.getLogger(UsuarioController.class);

    @Value("${cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${cookie.same-site:None}")
    private String cookieSameSite;

    private final UsuarioService usuarioService;
    private final EmailVerificacaoService emailVerificacaoService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioPendenteService usuarioPendenteService;
    private final UsuarioPendenteRepository usuarioPendenteRepository;
    private final UsuarioMapper usuarioMapper;

    public UsuarioController(UsuarioService usuarioService,
                             EmailVerificacaoService emailVerificacaoService,
                             RecuperacaoSenhaService recuperacaoSenhaService,
                             AuthenticationManager authenticationManager,
                             JwtService jwtService,
                             UsuarioRepository usuarioRepository,
                             PasswordEncoder passwordEncoder,
                             UsuarioPendenteService usuarioPendenteService,
                             UsuarioPendenteRepository usuarioPendenteRepository,
                             UsuarioMapper usuarioMapper) {
        this.usuarioService = usuarioService;
        this.emailVerificacaoService = emailVerificacaoService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.usuarioPendenteService = usuarioPendenteService;
        this.usuarioPendenteRepository = usuarioPendenteRepository;
        this.usuarioMapper = usuarioMapper;
    }

    @PostMapping("/pre-registrar")
    @Operation(summary = "Solicitar pré-registro", description = "Inicia o fluxo de cadastro com OTP. Retorna registroId para validação.")
    @Transactional
    public ResponseEntity<OtpStatusResponseDTO> preRegistrar(@Valid @RequestBody UsuarioRegistroRequestDTO dto) {
        String emailNorm = dto.email().trim().toLowerCase();
        LocalDateTime agora = LocalDateTime.now();
        
        // Anti-enumeração: se e-mail existe em 'usuarios', retorna sucesso genérico sem enviar OTP
        if (usuarioRepository.existsByEmailIncludingInactive(emailNorm)) {
            log.warn("[SECURITY] Tentativa de pré-registro com e-mail já existente: {}", emailNorm);
            // Anti-timing: simula latência de envio de e-mail
            try { Thread.sleep(1200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return ResponseEntity.ok(new OtpStatusResponseDTO("ATIVO", 5, agora.plusMinutes(15), null, null, null, UUID.randomUUID().toString()));
        }

        UsuarioPendenteEntity pendente = usuarioPendenteRepository.findByEmail(emailNorm)
                .orElseGet(() -> {
                    UsuarioPendenteEntity p = new UsuarioPendenteEntity();
                    p.setId(UUID.randomUUID());
                    p.setEmail(emailNorm);
                    p.setNome(dto.nome());
                    p.setSenhaHash(passwordEncoder.encode(dto.senha()));
                    p.setExpiraEm(agora.minusMinutes(1)); // Força inicialização
                    return p;
                });

        if (pendente.getBloqueadoAte() != null && pendente.getBloqueadoAte().isAfter(agora)) {
            return ResponseEntity.status(423).body(usuarioPendenteService.mapearParaStatus(pendente));
        }

        if (pendente.getUltimoEnvioEm() != null && pendente.getUltimoEnvioEm().plusMinutes(5).isAfter(agora)) {
            return ResponseEntity.status(429).body(usuarioPendenteService.mapearParaStatus(pendente));
        }

        if (pendente.getExpiraEm().isBefore(agora)) {
            pendente.setNome(dto.nome());
            pendente.setSenhaHash(passwordEncoder.encode(dto.senha()));
            pendente.setExpiraEm(agora.plusMinutes(15));
            pendente.setUltimoEnvioEm(agora);
            usuarioPendenteRepository.save(pendente);
            emailVerificacaoService.gerarCodigo(pendente.getEmail());
        } else {
            usuarioPendenteRepository.save(pendente);
        }

        return ResponseEntity.ok(usuarioPendenteService.mapearParaStatus(pendente));
    }

    @GetMapping("/otp-status")
    @Operation(summary = "Consultar status do OTP", description = "Retorna o estado atual do pré-registro.")
    public ResponseEntity<OtpStatusResponseDTO> getOtpStatus(@RequestParam String registroId) {
        UsuarioPendenteEntity pendente = usuarioPendenteService.buscarOuFalhar(UUID.fromString(registroId));
        return ResponseEntity.ok(usuarioPendenteService.mapearParaStatus(pendente));
    }

    @PostMapping("/login")
    @Operation(summary = "Login de usuário", description = "Autentica o usuário, seta refresh token em cookie HttpOnly e retorna access token.")
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public ResponseEntity<?> login(@Valid @RequestBody UsuarioLoginRequestDTO dto,
                                   HttpServletResponse httpResponse) {
        // Verificar lockout antes de tentar autenticação (G4-A1)
        UsuarioEntity usuarioCheck = usuarioRepository.findByEmail(dto.email()).orElse(null);
        if (usuarioCheck != null
                && usuarioCheck.getLockedUntil() != null
                && usuarioCheck.getLockedUntil().isAfter(LocalDateTime.now())) {
            long segundos = ChronoUnit.SECONDS.between(
                    LocalDateTime.now(), usuarioCheck.getLockedUntil());
            return ResponseEntity.status(423)
                    .body(Map.of("erro", "Conta bloqueada.",
                                 "desbloqueio_em_segundos", segundos));
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.senha())
            );

            UsuarioEntity principal = (UsuarioEntity) auth.getPrincipal();
            UsuarioEntity usuario = usuarioRepository.findByEmailWithLock(principal.getEmail())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

            if (!usuario.isEmailVerificado()) {
                log.warn("[SECURITY] Tentativa de login para e-mail não verificado: {}", usuario.getEmail());
                
                UsuarioPendenteEntity pendente = usuarioPendenteRepository.findByEmail(usuario.getEmail())
                        .orElseGet(() -> {
                            UsuarioPendenteEntity p = new UsuarioPendenteEntity();
                            p.setId(UUID.randomUUID());
                            p.setEmail(usuario.getEmail());
                            p.setNome(usuario.getNome());
                            p.setSenhaHash(usuario.getSenha());
                            p.setExpiraEm(LocalDateTime.now().plusMinutes(15));
                            return usuarioPendenteRepository.save(p);
                        });

                if (pendente.getUltimoEnvioEm() == null || pendente.getUltimoEnvioEm().plusMinutes(5).isBefore(LocalDateTime.now())) {
                    emailVerificacaoService.gerarCodigo(pendente.getEmail());
                    pendente.setUltimoEnvioEm(LocalDateTime.now());
                    usuarioPendenteRepository.save(pendente);
                }

                AuthResponseDTO errorResponse = new AuthResponseDTO(null, null, 0, null, usuarioPendenteService.mapearParaStatus(pendente));
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }

            usuario.setLoginAttempts(0);
            usuario.setLockedUntil(null);

            usuario.setChaveSessao(UUID.randomUUID().toString());
            usuarioRepository.save(usuario);

            String accessToken = jwtService.generateAccessToken(usuario);
            String refreshToken = jwtService.generateRefreshToken(usuario);

            setRefreshTokenCookie(httpResponse, refreshToken);

            AuthResponseDTO response = new AuthResponseDTO(
                    accessToken,
                    null,
                    jwtService.getAccessTokenExpiration(),
                    usuarioMapper.toResponse(usuario),
                    null
            );
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException ex) {
            UsuarioEntity usuarioFailed = usuarioRepository.findByEmailWithLock(dto.email()).orElse(null);
            if (usuarioFailed != null) {
                int tentativas = (usuarioFailed.getLoginAttempts() != null ? usuarioFailed.getLoginAttempts() : 0) + 1;
                usuarioFailed.setLoginAttempts(tentativas);
                if (tentativas >= 10) {
                    usuarioFailed.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                    usuarioFailed.setLoginAttempts(0);
                }
                usuarioRepository.save(usuarioFailed);
            }
            // Anti-timing delay
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
            throw ex;
        }
    }

    /** Rotaciona RT a cada uso (G5-A2). Reuso detectado → invalida todas as sessões (G5-A3). */
    @PostMapping("/refresh")
    @Operation(summary = "Renovar token", description = "Gera novo access token + rotaciona refresh token via cookie HttpOnly.")
    @Transactional
    public ResponseEntity<AuthResponseDTO> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse httpResponse) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String email;
        try {
            email = jwtService.extractUsername(refreshToken);
        } catch (JwtException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Lock pessimista serializa /refresh concorrentes com mesmo RT (B2 — race condition).
        UsuarioEntity usuario = usuarioRepository.findByEmailWithLock(email)
                .orElse(null);
        if (usuario == null) {
            // Usuário deletado/desativado após emissão do RT → 401 silencioso, sem expor estado.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!jwtService.isTokenValid(refreshToken, usuario)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String chaveSessaoToken = jwtService.extractChaveSessao(refreshToken);
        if (chaveSessaoToken == null || !chaveSessaoToken.equals(usuario.getChaveSessao())) {
            // RT reuse attack detectado — invalida todas as sessões
            usuario.setChaveSessao(null);
            usuarioRepository.save(usuario);
            clearRefreshTokenCookie(httpResponse);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Rotaciona: nova chaveSessao + novo RT
        usuario.setChaveSessao(UUID.randomUUID().toString());
        usuarioRepository.save(usuario);

        String newAccessToken = jwtService.generateAccessToken(usuario);
        String newRefreshToken = jwtService.generateRefreshToken(usuario);
        setRefreshTokenCookie(httpResponse, newRefreshToken);

        AuthResponseDTO response = new AuthResponseDTO(
                newAccessToken,
                null,
                jwtService.getAccessTokenExpiration(),
                usuarioMapper.toResponse(usuario),
                null
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalida a sessão do usuário e remove o cookie de refresh token.")
    @Transactional
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse httpResponse) {

        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                String email = jwtService.extractUsername(refreshToken);
                // Lock pessimista evita race com /refresh concorrente.
                usuarioRepository.findByEmailWithLock(email).ifPresent(u -> {
                    u.setChaveSessao(null);
                    usuarioRepository.save(u);
                });
            } catch (JwtException ignored) {
                // Token inválido — limpar cookie mesmo assim
            }
        }
        clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verificar-email")
    @Operation(summary = "Verificar e-mail", description = "Valida a conta usando o código de 6 dígitos. Suporta fluxo de pré-registro.")
    @Transactional
    public ResponseEntity<?> verificarEmail(@Valid @RequestBody VerificarEmailRequestDTO dto) {
        // Fluxo via registroId (Pré-registro)
        if (dto.registroId() != null && !dto.registroId().isBlank()) {
            UsuarioPendenteEntity pendente = usuarioPendenteService.buscarOuFalharComLock(UUID.fromString(dto.registroId()));
            
            if (pendente.getBloqueadoAte() != null && pendente.getBloqueadoAte().isAfter(LocalDateTime.now())) {
                long segundos = ChronoUnit.SECONDS.between(LocalDateTime.now(), pendente.getBloqueadoAte());
                return ResponseEntity.status(423)
                        .header("Retry-After", String.valueOf(segundos))
                        .body(usuarioPendenteService.mapearParaStatus(pendente));
            }

            try {
                emailVerificacaoService.validarCodigoSimples(pendente.getEmail(), dto.codigo());
                
                // OTP Válido -> Promover para usuário real e limpar rastro
                UsuarioEntity usuario = usuarioService.finalizarRegistro(pendente);
                usuarioPendenteRepository.delete(pendente);
                
                log.info("E-mail verificado com sucesso via pré-registro: {}", usuario.getEmail());
                return ResponseEntity.ok(Map.of("mensagem", "E-mail verificado com sucesso!", "email", usuario.getEmail()));
            } catch (Exception e) {
                usuarioPendenteService.registrarTentativaFalha(pendente);
                throw e;
            }
        }

        // Fluxo Legado (apenas e-mail) - Mantido para compatibilidade interna
        emailVerificacaoService.verificarEmail(dto);
        return ResponseEntity.ok("E-mail verificado com sucesso!");
    }

    @PostMapping("/reenviar-codigo")
    @Operation(summary = "Reenviar código", description = "Gera um novo código de ativação. Suporta registroId.")
    @Transactional
    public ResponseEntity<?> reenviarCodigo(@Valid @RequestBody ReenviarCodigoRequestDTO dto) {
        if (dto.registroId() != null && !dto.registroId().isBlank()) {
            UsuarioPendenteEntity pendente = usuarioPendenteService.buscarOuFalhar(UUID.fromString(dto.registroId()));
            
            if (pendente.getBloqueadoAte() != null && pendente.getBloqueadoAte().isAfter(LocalDateTime.now())) {
                long segundos = ChronoUnit.SECONDS.between(LocalDateTime.now(), pendente.getBloqueadoAte());
                return ResponseEntity.status(423)
                        .header("Retry-After", String.valueOf(segundos))
                        .body(usuarioPendenteService.mapearParaStatus(pendente));
            }

            if (pendente.getUltimoEnvioEm() != null && pendente.getUltimoEnvioEm().plusMinutes(5).isAfter(LocalDateTime.now())) {
                long segundos = ChronoUnit.SECONDS.between(LocalDateTime.now(), pendente.getUltimoEnvioEm().plusMinutes(5));
                return ResponseEntity.status(429)
                        .header("Retry-After", String.valueOf(segundos))
                        .body(usuarioPendenteService.mapearParaStatus(pendente));
            }

            emailVerificacaoService.gerarCodigo(pendente.getEmail());
            pendente.setUltimoEnvioEm(LocalDateTime.now());
            usuarioPendenteRepository.save(pendente);
            
            return ResponseEntity.ok(usuarioPendenteService.mapearParaStatus(pendente));
        }

        emailVerificacaoService.reenviarCodigo(dto);
        return ResponseEntity.ok("Novo código de verificação enviado!");
    }

    /** Exige confirmação de senha antes de reativar. */
    @PostMapping("/reativar-conta")
    @Operation(summary = "Reativar conta", description = "Reativa uma conta desativada após confirmação de identidade via senha.")
    public ResponseEntity<String> reativarConta(@Valid @RequestBody ReativarContaRequestDTO dto) {
        usuarioService.reativarConta(dto.email(), dto.senha());
        return ResponseEntity.ok("Conta reativada com sucesso!");
    }

    // ─── Recuperação de Senha ──────────────────────────────────────────────

    /** Anti-enumeração: resposta sempre 200 OK independente de o e-mail existir. Timing fixo anti-enumeration. */
    @PostMapping("/solicitar-recuperacao")
    @Operation(summary = "Solicitar recuperação de senha", description = "Envia um link de recuperação de senha para o e-mail informado.")
    public ResponseEntity<String> solicitarRecuperacao(@Valid @RequestBody SolicitarRecuperacaoSenhaRequestDTO dto) {
        long inicio = System.currentTimeMillis();
        try {
            recuperacaoSenhaService.solicitarRecuperacao(dto);
        } finally {
            long elapsed = System.currentTimeMillis() - inicio;
            long restante = 1200 - elapsed;
            if (restante > 0) {
                try { Thread.sleep(restante); } catch (InterruptedException ignored) {}
            }
        }
        return ResponseEntity.ok("Se o e-mail estiver cadastrado, você receberá um link de recuperação.");
    }

    /** Chamado pelo frontend ao carregar a página de reset para validar o token antecipadamente. */
    @GetMapping("/validar-token")
    @Operation(summary = "Validar token de recuperação", description = "Verifica se o token é válido e retorna o e-mail associado.")
    public ResponseEntity<Map<String, String>> validarToken(@RequestParam String token) {
        String email = recuperacaoSenhaService.validarToken(token);
        return ResponseEntity.ok(Map.of("email", email));
    }

    /** Após o reset, sessões ativas são invalidadas — requer novo login. */
    @PostMapping("/resetar-senha")
    @Operation(summary = "Resetar senha", description = "Redefine a senha do usuário usando o token de recuperação enviado por e-mail.")
    public ResponseEntity<String> resetarSenha(@Valid @RequestBody ResetarSenhaRequestDTO dto) {
        recuperacaoSenhaService.resetarSenha(dto);
        return ResponseEntity.ok("Senha redefinida com sucesso! Faça login com sua nova senha.");
    }

    // ─── Helpers de Cookie ────────────────────────────────────────────────

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(jwtService.getRefreshTokenExpiration() / 1000)
                .sameSite(cookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(0)
                .sameSite(cookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
