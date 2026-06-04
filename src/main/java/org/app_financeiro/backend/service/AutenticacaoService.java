package org.app_financeiro.backend.service;

import io.jsonwebtoken.JwtException;
import org.app_financeiro.backend.dto.request.ReenviarCodigoRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarAcaoContaRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.dto.response.OtpStatusResponseDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.entity.UsuarioPendenteEntity;
import org.app_financeiro.backend.enums.TipoCodigoVerificacao;
import org.app_financeiro.backend.exception.CodigoVerificacaoInvalidoException;
import org.app_financeiro.backend.exception.CredenciaisInvalidasException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Lógica de negócio de autenticação extraída do UsuarioController.
 * O controller delega para cá; apenas montagem de cookies e ResponseEntity ficam no controller.
 */
@Service
public class AutenticacaoService {

    private static final Logger log = LoggerFactory.getLogger(AutenticacaoService.class);

    private final UsuarioRepository usuarioRepository;
    private final UsuarioPendenteRepository usuarioPendenteRepository;
    private final CodigoVerificacaoRepository codigoVerificacaoRepository;
    private final UsuarioPendenteService usuarioPendenteService;
    private final EmailVerificacaoService emailVerificacaoService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioService usuarioService;

    public AutenticacaoService(UsuarioRepository usuarioRepository,
                               UsuarioPendenteRepository usuarioPendenteRepository,
                               CodigoVerificacaoRepository codigoVerificacaoRepository,
                               UsuarioPendenteService usuarioPendenteService,
                               EmailVerificacaoService emailVerificacaoService,
                               JwtService jwtService,
                               AuthenticationManager authenticationManager,
                               PasswordEncoder passwordEncoder,
                               UsuarioService usuarioService) {
        this.usuarioRepository = usuarioRepository;
        this.usuarioPendenteRepository = usuarioPendenteRepository;
        this.codigoVerificacaoRepository = codigoVerificacaoRepository;
        this.usuarioPendenteService = usuarioPendenteService;
        this.emailVerificacaoService = emailVerificacaoService;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.usuarioService = usuarioService;
    }

    // ── Pré-registro ────────────────────────────────────────────────────────

    public record PreRegistroResultado(int httpStatus, OtpStatusResponseDTO otpStatus) {}

    @Transactional
    public PreRegistroResultado preRegistrar(UsuarioRegistroRequestDTO dto) {
        String emailNorm = dto.email().trim().toLowerCase();
        LocalDateTime agora = LocalDateTime.now();

        if (usuarioRepository.existsByEmailIncludingInactive(emailNorm)) {
            log.warn("[SECURITY] Tentativa de pré-registro com e-mail já existente: {}", emailNorm);
            usuarioRepository.findByEmailIncludingInactive(emailNorm)
                    .ifPresent(u -> emailVerificacaoService.enviarAvisoTentativaCadastro(u.getEmail(), u.getNome()));
            try { Thread.sleep(1200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            OffsetDateTime expiraEm = agora.plusMinutes(15).atZone(ZoneId.systemDefault()).toOffsetDateTime();
            return new PreRegistroResultado(200,
                    new OtpStatusResponseDTO("ATIVO", 5, expiraEm, null, null, null, UUID.randomUUID().toString()));
        }

        UsuarioPendenteEntity pendente = usuarioPendenteRepository.findByEmail(emailNorm)
                .orElseGet(() -> {
                    UsuarioPendenteEntity p = new UsuarioPendenteEntity();
                    p.setId(UUID.randomUUID());
                    p.setEmail(emailNorm);
                    p.setNome(dto.nome());
                    p.setSenhaHash(passwordEncoder.encode(dto.senha()));
                    p.setExpiraEm(agora.minusMinutes(1));
                    return p;
                });

        if (pendente.getBloqueadoAte() != null && pendente.getBloqueadoAte().isAfter(agora)) {
            return new PreRegistroResultado(423, usuarioPendenteService.mapearParaStatus(pendente));
        }

        if (pendente.getUltimoEnvioEm() != null && pendente.getUltimoEnvioEm().plusMinutes(1).isAfter(agora)) {
            return new PreRegistroResultado(429, usuarioPendenteService.mapearParaStatus(pendente));
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

        return new PreRegistroResultado(200, usuarioPendenteService.mapearParaStatus(pendente));
    }

    // ── Login ────────────────────────────────────────────────────────────────

    public sealed interface LoginResultado permits
            LoginResultado.Bloqueado,
            LoginResultado.EmailNaoVerificado,
            LoginResultado.Sucesso,
            LoginResultado.ContaDesativada,
            LoginResultado.CredenciaisInvalidas {

        record Bloqueado(long segundosRestantes) implements LoginResultado {}
        record EmailNaoVerificado(OtpStatusResponseDTO otpStatus) implements LoginResultado {}
        record Sucesso(UsuarioEntity usuario, String accessToken, String refreshToken) implements LoginResultado {}
        record ContaDesativada(String emailMascarado) implements LoginResultado {}
        record CredenciaisInvalidas() implements LoginResultado {}
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public LoginResultado login(UsuarioLoginRequestDTO dto) {
        UsuarioEntity usuarioCheck = usuarioRepository.findByEmail(dto.email()).orElse(null);
        if (usuarioCheck != null
                && usuarioCheck.getLockedUntil() != null
                && usuarioCheck.getLockedUntil().isAfter(LocalDateTime.now())) {
            long segundos = ChronoUnit.SECONDS.between(LocalDateTime.now(), usuarioCheck.getLockedUntil());
            return new LoginResultado.Bloqueado(segundos);
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.senha()));

            UsuarioEntity principal = (UsuarioEntity) auth.getPrincipal();
            UsuarioEntity usuario = usuarioRepository.findByEmailWithLock(principal.getEmail())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

            if (!usuario.isEmailVerificado()) {
                log.warn("[SECURITY] Login com e-mail não verificado: {}", usuario.getEmail());
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
                if (pendente.getUltimoEnvioEm() == null
                        || pendente.getUltimoEnvioEm().plusMinutes(1).isBefore(LocalDateTime.now())) {
                    emailVerificacaoService.gerarCodigo(pendente.getEmail());
                    pendente.setUltimoEnvioEm(LocalDateTime.now());
                    usuarioPendenteRepository.save(pendente);
                }
                return new LoginResultado.EmailNaoVerificado(usuarioPendenteService.mapearParaStatus(pendente));
            }

            usuario.setLoginAttempts(0);
            usuario.setLockedUntil(null);
            usuario.setChaveSessao(UUID.randomUUID().toString());
            usuarioRepository.save(usuario);

            String accessToken = jwtService.generateAccessToken(usuario);
            String refreshToken = jwtService.generateRefreshToken(usuario);
            return new LoginResultado.Sucesso(usuario, accessToken, refreshToken);

        } catch (BadCredentialsException e) {
            Optional<UsuarioEntity> inativoOpt = usuarioRepository.findInactiveByEmail(dto.email());
            if (inativoOpt.isPresent()) {
                UsuarioEntity inativo = inativoOpt.get();
                if (passwordEncoder.matches(dto.senha(), inativo.getSenha())) {
                    emailVerificacaoService.gerarCodigo(inativo.getEmail(), TipoCodigoVerificacao.REATIVACAO_CONTA);
                    return new LoginResultado.ContaDesativada(mascararEmail(inativo.getEmail()));
                }
            }
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
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
            return new LoginResultado.CredenciaisInvalidas();
        }
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    public record RefreshResultado(boolean invalido, boolean reuso, UsuarioEntity usuario,
                                   String accessToken, String refreshToken) {
        public static RefreshResultado tokenInvalido() { return new RefreshResultado(true, false, null, null, null); }
        public static RefreshResultado tokenReutilizado() { return new RefreshResultado(false, true, null, null, null); }
    }

    @Transactional
    public RefreshResultado refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return RefreshResultado.tokenInvalido();

        String email;
        try {
            email = jwtService.extractUsername(refreshToken);
        } catch (JwtException ex) {
            return RefreshResultado.tokenInvalido();
        }

        UsuarioEntity usuario = usuarioRepository.findByEmailWithLock(email).orElse(null);
        if (usuario == null || !jwtService.isTokenValid(refreshToken, usuario)) return RefreshResultado.tokenInvalido();

        String chaveSessaoToken = jwtService.extractChaveSessao(refreshToken);
        if (chaveSessaoToken == null || !chaveSessaoToken.equals(usuario.getChaveSessao())) {
            usuario.setChaveSessao(null);
            usuarioRepository.save(usuario);
            return RefreshResultado.tokenReutilizado();
        }

        usuario.setChaveSessao(UUID.randomUUID().toString());
        usuarioRepository.save(usuario);

        return new RefreshResultado(false, false, usuario,
                jwtService.generateAccessToken(usuario),
                jwtService.generateRefreshToken(usuario));
    }

    // ── Logout ───────────────────────────────────────────────────────────────

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        try {
            String email = jwtService.extractUsername(refreshToken);
            usuarioRepository.findByEmailWithLock(email).ifPresent(u -> {
                u.setChaveSessao(null);
                usuarioRepository.save(u);
            });
        } catch (JwtException ignored) {}
    }

    // ── Verificar e-mail ─────────────────────────────────────────────────────

    public sealed interface VerificarEmailResultado permits
            VerificarEmailResultado.Bloqueado,
            VerificarEmailResultado.Sucesso,
            VerificarEmailResultado.CodigoInvalido,
            VerificarEmailResultado.SucessoLegado {

        record Bloqueado(OtpStatusResponseDTO otpStatus) implements VerificarEmailResultado {}
        record Sucesso(String email) implements VerificarEmailResultado {}
        record CodigoInvalido(OtpStatusResponseDTO otpStatus) implements VerificarEmailResultado {}
        record SucessoLegado() implements VerificarEmailResultado {}
    }

    @Transactional(noRollbackFor = CodigoVerificacaoInvalidoException.class)
    public VerificarEmailResultado verificarEmail(VerificarEmailRequestDTO dto) {
        if (dto.registroId() != null && !dto.registroId().isBlank()) {
            UsuarioPendenteEntity pendente = usuarioPendenteService.buscarOuFalharComLock(UUID.fromString(dto.registroId()));
            if (pendente.getBloqueadoAte() != null && pendente.getBloqueadoAte().isAfter(LocalDateTime.now())) {
                return new VerificarEmailResultado.Bloqueado(usuarioPendenteService.mapearParaStatus(pendente));
            }
            try {
                emailVerificacaoService.validarCodigoSimples(pendente.getEmail(), dto.codigo(), TipoCodigoVerificacao.VERIFICACAO_EMAIL);
                UsuarioEntity usuario = usuarioService.finalizarRegistro(pendente);
                usuarioPendenteRepository.delete(pendente);
                log.info("E-mail verificado via pré-registro: {}", usuario.getEmail());
                return new VerificarEmailResultado.Sucesso(usuario.getEmail());
            } catch (CodigoVerificacaoInvalidoException ex) {
                usuarioPendenteService.registrarTentativaFalha(pendente);
                if (pendente.getBloqueadoAte() != null && pendente.getBloqueadoAte().isAfter(LocalDateTime.now())) {
                    return new VerificarEmailResultado.Bloqueado(usuarioPendenteService.mapearParaStatus(pendente));
                }
                return new VerificarEmailResultado.CodigoInvalido(usuarioPendenteService.mapearParaStatus(pendente));
            }
        }
        emailVerificacaoService.verificarEmail(dto);
        return new VerificarEmailResultado.SucessoLegado();
    }

    // ── Reenviar código ──────────────────────────────────────────────────────

    public record ReenviarResultado(int httpStatus, OtpStatusResponseDTO otpStatus, boolean legado) {}

    @Transactional
    public ReenviarResultado reenviarCodigo(ReenviarCodigoRequestDTO dto) {
        if (dto.registroId() != null && !dto.registroId().isBlank()) {
            UsuarioPendenteEntity pendente = usuarioPendenteService.buscarOuFalhar(UUID.fromString(dto.registroId()));
            if (pendente.getBloqueadoAte() != null && pendente.getBloqueadoAte().isAfter(LocalDateTime.now())) {
                return new ReenviarResultado(423, usuarioPendenteService.mapearParaStatus(pendente), false);
            }
            if (pendente.getUltimoEnvioEm() != null && pendente.getUltimoEnvioEm().plusMinutes(1).isAfter(LocalDateTime.now())) {
                return new ReenviarResultado(429, usuarioPendenteService.mapearParaStatus(pendente), false);
            }
            emailVerificacaoService.gerarCodigo(pendente.getEmail());
            pendente.setUltimoEnvioEm(LocalDateTime.now());
            usuarioPendenteRepository.save(pendente);
            return new ReenviarResultado(200, usuarioPendenteService.mapearParaStatus(pendente), false);
        }
        emailVerificacaoService.reenviarCodigo(dto);
        return new ReenviarResultado(200, null, true);
    }

    // ── Solicitar ação de conta ───────────────────────────────────────────────

    public record CooldownInfo(long segundosRestantes) {}

    @Transactional
    public Optional<CooldownInfo> solicitarAcaoConta(SolicitarAcaoContaRequestDTO dto, UsuarioEntity usuario) {
        if (!passwordEncoder.matches(dto.senha(), usuario.getSenha())) {
            log.warn("[SECURITY] Senha inválida ao solicitar ação de conta: {}", mascararEmail(usuario.getEmail()));
            throw new CredenciaisInvalidasException();
        }
        TipoCodigoVerificacao tipo = "EXCLUIR".equalsIgnoreCase(dto.acao())
                ? TipoCodigoVerificacao.EXCLUSAO_CONTA
                : TipoCodigoVerificacao.DESATIVACAO_CONTA;

        Optional<CooldownInfo> cooldown = verificarCooldownAcaoConta(usuario.getEmail(), tipo);
        if (cooldown.isPresent()) return cooldown;

        codigoVerificacaoRepository.invalidarTodosPendentesPorTipo(usuario.getEmail(), tipo);
        emailVerificacaoService.gerarCodigo(usuario.getEmail(), tipo);
        return Optional.empty();
    }

    private Optional<CooldownInfo> verificarCooldownAcaoConta(String email, TipoCodigoVerificacao tipo) {
        if (email == null || email.isBlank()) return Optional.empty();
        CodigoVerificacaoEntity ultimo = codigoVerificacaoRepository
                .findTopByEmailAndTipoOrderByDataCriacaoDesc(email, tipo)
                .orElse(null);
        if (ultimo == null || ultimo.getDataCriacao() == null) return Optional.empty();
        LocalDateTime proximoEnvio = ultimo.getDataCriacao().plusMinutes(1);
        if (!proximoEnvio.isAfter(LocalDateTime.now())) return Optional.empty();
        long segundos = ChronoUnit.SECONDS.between(LocalDateTime.now(), proximoEnvio);
        return Optional.of(new CooldownInfo(segundos));
    }

    public String mascararEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int at = email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
