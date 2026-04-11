package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.ReativarContaRequestDTO;
import org.app_financeiro.backend.dto.request.ReenviarCodigoRequestDTO;
import org.app_financeiro.backend.dto.request.ResetarSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarRecuperacaoSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.dto.response.AuthResponseDTO;
import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.app_financeiro.backend.service.EmailVerificacaoService;
import org.app_financeiro.backend.service.JwtService;
import org.app_financeiro.backend.service.RecuperacaoSenhaService;
import org.app_financeiro.backend.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.app_financeiro.backend.mapper.UsuarioMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Controller responsável pelos endpoints de autenticação e verificação de e-mail.
 *
 * Endpoints:
 * - POST /api/auth/registrar       → Cria novo usuário (emailVerificado=false)
 * - POST /api/auth/login           → Autentica usuário e retorna access + refresh tokens
 * - POST /api/auth/refresh         → Renova o access token usando o refresh token
 * - POST /api/auth/verificar-email → Valida código de 6 dígitos
 * - POST /api/auth/reenviar-codigo → Gera e reenvia novo código de verificação
 * - POST /api/auth/solicitar-recuperacao → Envia link de recuperação de senha por e-mail
 * - GET  /api/auth/validar-token   → Valida se o token de recuperação é válido
 * - POST /api/auth/resetar-senha   → Reseta a senha usando o token de recuperação
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Fluxos de registro, login e verificação de conta")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final EmailVerificacaoService emailVerificacaoService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioMapper usuarioMapper;

    public UsuarioController(UsuarioService usuarioService,
                             EmailVerificacaoService emailVerificacaoService,
                             RecuperacaoSenhaService recuperacaoSenhaService,
                             AuthenticationManager authenticationManager,
                             JwtService jwtService,
                             UsuarioRepository usuarioRepository,
                             UsuarioMapper usuarioMapper) {
        this.usuarioService = usuarioService;
        this.emailVerificacaoService = emailVerificacaoService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
        this.usuarioMapper = usuarioMapper;
    }

    /**
     * Registra um novo usuário.
     * Após o registro, gera automaticamente um código de verificação de e-mail.
     *
     * @param dto dados do novo usuário (nome, email, senha)
     * @return 201 Created com os dados do usuário criado
     */
    @PostMapping("/registrar")
    @Operation(summary = "Registrar novo usuário", description = "Cria uma conta pendente de verificação.")
    public ResponseEntity<UsuarioResponseDTO> registrar(@Valid @RequestBody UsuarioRegistroRequestDTO dto) {
        UsuarioResponseDTO usuario = usuarioService.registrarUsuario(dto);

        // Gera código de verificação automaticamente após registro
        emailVerificacaoService.gerarCodigo(dto.email());

        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    /**
     * Autentica o usuário por e-mail e senha.
     * Retorna AuthResponseDTO contendo accessToken, refreshToken e expiresIn.
     *
     * @param dto credenciais (email, senha)
     * @return 200 OK com tokens de autenticação
     */
    @PostMapping("/login")
    @Operation(summary = "Login de usuário", description = "Autentica o usuário e retorna os tokens de acesso e renovação.")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody UsuarioLoginRequestDTO dto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.email(), dto.senha())
        );

        UsuarioEntity usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        // Geração da chave de sessão única (Single Active Session)
        usuario.setChaveSessao(UUID.randomUUID().toString());
        usuarioRepository.save(usuario);

        String accessToken = jwtService.generateAccessToken(usuario);
        String refreshToken = jwtService.generateRefreshToken(usuario);

        AuthResponseDTO response = new AuthResponseDTO(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenExpiration(),
                usuarioMapper.toResponse(usuario)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Renova o access token usando um refresh token válido.
     * O refresh token é enviado no corpo da requisição.
     *
     * @param body mapa contendo a chave "refreshToken"
     * @return 200 OK com novo accessToken, mesmo refreshToken e expiresIn
     */
    @PostMapping("/refresh")
    @Operation(summary = "Renovar token", description = "Gera um novo access token a partir de um refresh token válido.")
    public ResponseEntity<AuthResponseDTO> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String email;
        try {
            email = jwtService.extractUsername(refreshToken);
        } catch (JwtException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UsuarioEntity usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        if (!jwtService.isTokenValid(refreshToken, usuario)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String chaveSessaoToken = jwtService.extractChaveSessao(refreshToken);
        if (chaveSessaoToken == null || !chaveSessaoToken.equals(usuario.getChaveSessao())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String newAccessToken = jwtService.generateAccessToken(usuario);

        AuthResponseDTO response = new AuthResponseDTO(
                newAccessToken,
                refreshToken,
                jwtService.getAccessTokenExpiration(),
                usuarioMapper.toResponse(usuario)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Verifica o e-mail do usuário usando o código de 6 dígitos recebido.
     *
     * @param dto contém email e código de verificação
     * @return 200 OK com mensagem de sucesso
     */
    @PostMapping("/verificar-email")
    @Operation(summary = "Verificar e-mail", description = "Valida a conta do usuário usando o código de 6 dígitos enviado por e-mail.")
    public ResponseEntity<String> verificarEmail(@Valid @RequestBody VerificarEmailRequestDTO dto) {
        emailVerificacaoService.verificarEmail(dto);
        return ResponseEntity.ok("E-mail verificado com sucesso!");
    }

    /**
     * Reenvia um novo código de verificação para o e-mail informado.
     * Invalida códigos anteriores (se implementado no service).
     *
     * @param dto contém o email para reenvio
     * @return 200 OK com mensagem de sucesso
     */
    @PostMapping("/reenviar-codigo")
    @Operation(summary = "Reenviar código", description = "Gera um novo código de ativação e reenvia para o e-mail do usuário.")
    public ResponseEntity<String> reenviarCodigo(@Valid @RequestBody ReenviarCodigoRequestDTO dto) {
        emailVerificacaoService.reenviarCodigo(dto);
        return ResponseEntity.ok("Novo código de verificação enviado!");
    }

    /**
     * Reativa uma conta previamente desativada (soft delete).
     * Exige confirmação de senha para validar a identidade.
     *
     * @param dto contém email e senha para confirmação
     * @return 200 OK com mensagem de sucesso
     */
    @PostMapping("/reativar-conta")
    @Operation(summary = "Reativar conta", description = "Reativa uma conta desativada após confirmação de identidade via senha.")
    public ResponseEntity<String> reativarConta(@Valid @RequestBody ReativarContaRequestDTO dto) {
        usuarioService.reativarConta(dto.email(), dto.senha());
        return ResponseEntity.ok("Conta reativada com sucesso!");
    }

    // ─── Recuperação de Senha ──────────────────────────────────────────────

    /**
     * Solicita a recuperação de senha. Envia um link por e-mail.
     * A resposta é sempre 200 OK, mesmo que o e-mail não exista (proteção contra enumeração).
     *
     * @param dto contém o e-mail para recuperação
     * @return 200 OK com mensagem genérica
     */
    @PostMapping("/solicitar-recuperacao")
    @Operation(summary = "Solicitar recuperação de senha", description = "Envia um link de recuperação de senha para o e-mail informado.")
    public ResponseEntity<String> solicitarRecuperacao(@Valid @RequestBody SolicitarRecuperacaoSenhaRequestDTO dto) {
        recuperacaoSenhaService.solicitarRecuperacao(dto);
        return ResponseEntity.ok("Se o e-mail estiver cadastrado, você receberá um link de recuperação.");
    }

    /**
     * Valida se o token de recuperação é legítimo e ainda está dentro da validade.
     * O frontend chama este endpoint ao carregar a página de reset.
     *
     * @param token UUID enviado por e-mail
     * @return 200 OK com o e-mail vinculado ao token
     */
    @GetMapping("/validar-token")
    @Operation(summary = "Validar token de recuperação", description = "Verifica se o token é válido e retorna o e-mail associado.")
    public ResponseEntity<Map<String, String>> validarToken(@RequestParam String token) {
        String email = recuperacaoSenhaService.validarToken(token);
        return ResponseEntity.ok(Map.of("email", email));
    }

    /**
     * Reseta a senha do usuário usando o token de recuperação.
     * Após o reset, o usuário deve fazer login manualmente.
     *
     * @param dto contém token e nova senha
     * @return 200 OK com mensagem de sucesso
     */
    @PostMapping("/resetar-senha")
    @Operation(summary = "Resetar senha", description = "Redefine a senha do usuário usando o token de recuperação enviado por e-mail.")
    public ResponseEntity<String> resetarSenha(@Valid @RequestBody ResetarSenhaRequestDTO dto) {
        recuperacaoSenhaService.resetarSenha(dto);
        return ResponseEntity.ok("Senha redefinida com sucesso! Faça login com sua nova senha.");
    }
}
