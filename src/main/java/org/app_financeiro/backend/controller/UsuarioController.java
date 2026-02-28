package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.ReenviarCodigoRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.service.EmailVerificacaoService;
import org.app_financeiro.backend.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller responsável pelos endpoints de autenticação e verificação de e-mail.
 *
 * Endpoints:
 * - POST /api/auth/registrar     → Cria novo usuário (emailVerificado=false)
 * - POST /api/auth/login         → Autentica usuário (exige e-mail verificado)
 * - POST /api/auth/verificar-email → Valida código de 6 dígitos
 * - POST /api/auth/reenviar-codigo → Gera e reenvia novo código de verificação
 *
 * NOTA: Por enquanto não há segurança com JWT/Spring Security.
 * O login retorna o UsuarioResponseDTO diretamente.
 * Quando JWT for implementado, o login retornará um token no lugar.
 */
@RestController
@RequestMapping("/api/auth")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final EmailVerificacaoService emailVerificacaoService;

    public UsuarioController(UsuarioService usuarioService,
                             EmailVerificacaoService emailVerificacaoService) {
        this.usuarioService = usuarioService;
        this.emailVerificacaoService = emailVerificacaoService;
    }

    /**
     * Registra um novo usuário.
     * Após o registro, gera automaticamente um código de verificação de e-mail.
     *
     * @param dto dados do novo usuário (nome, email, senha)
     * @return 201 Created com os dados do usuário criado
     */
    @PostMapping("/registrar")
    public ResponseEntity<UsuarioResponseDTO> registrar(@Valid @RequestBody UsuarioRegistroRequestDTO dto) {
        UsuarioResponseDTO usuario = usuarioService.registrarUsuario(dto);

        // Gera código de verificação automaticamente após registro
        emailVerificacaoService.gerarCodigo(dto.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    /**
     * Autentica o usuário por e-mail e senha.
     * Exige que o e-mail esteja verificado.
     *
     * @param dto credenciais (email, senha)
     * @return 200 OK com os dados do usuário autenticado
     */
    @PostMapping("/login")
    public ResponseEntity<UsuarioResponseDTO> login(@Valid @RequestBody UsuarioLoginRequestDTO dto) {
        UsuarioResponseDTO usuario = usuarioService.loginUsuario(dto.getEmail(), dto.getSenha());
        return ResponseEntity.ok(usuario);
    }

    /**
     * Verifica o e-mail do usuário usando o código de 6 dígitos recebido.
     *
     * @param dto contém email e código de verificação
     * @return 200 OK com mensagem de sucesso
     */
    @PostMapping("/verificar-email")
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
    public ResponseEntity<String> reenviarCodigo(@Valid @RequestBody ReenviarCodigoRequestDTO dto) {
        emailVerificacaoService.reenviarCodigo(dto);
        return ResponseEntity.ok("Novo código de verificação enviado!");
    }
}
