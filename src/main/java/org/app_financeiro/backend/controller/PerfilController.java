package org.app_financeiro.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.AlterarSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.ConfirmarAlteracaoEmailRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarAlteracaoEmailRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioAtualizacaoRequestDTO;
import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.dto.response.PerfilResumoResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.mapper.UsuarioMapper;
import org.app_financeiro.backend.service.AlteracaoEmailService;
import org.app_financeiro.backend.service.UsuarioService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Gestão de perfil do usuário autenticado. */
@RestController
@RequestMapping("/api/usuarios/perfil")
@Tag(name = "Perfil", description = "Gestão de dados do perfil do usuário")
public class PerfilController {

    private final UsuarioService usuarioService;
    private final AlteracaoEmailService alteracaoEmailService;
    private final UsuarioMapper usuarioMapper;

    public PerfilController(UsuarioService usuarioService,
                            AlteracaoEmailService alteracaoEmailService,
                            UsuarioMapper usuarioMapper) {
        this.usuarioService = usuarioService;
        this.alteracaoEmailService = alteracaoEmailService;
        this.usuarioMapper = usuarioMapper;
    }

    @GetMapping("/me")
    @Operation(summary = "Obter dados do perfil", description = "Retorna os dados do usuário autenticado através do token.")
    public ResponseEntity<UsuarioResponseDTO> obterPerfil(@AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(usuarioMapper.toResponse(usuario));
    }

    @PutMapping("/me")
    @Operation(summary = "Atualizar perfil", description = "Atualiza nome, celular e preferência de moeda do usuário logado.")
    public ResponseEntity<UsuarioResponseDTO> atualizarPerfil(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @Valid @RequestBody UsuarioAtualizacaoRequestDTO dto) {
        
        UsuarioResponseDTO atualizado = usuarioService.atualizarPerfil(usuario.getId(), dto);
        return ResponseEntity.ok(atualizado);
    }

    @PatchMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de foto", description = "Realiza o upload real da foto de perfil para armazenamento local.")
    public ResponseEntity<Void> atualizarFoto(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @RequestParam("file") MultipartFile file) {
        
        usuarioService.atualizarFoto(usuario.getId(), file);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/senha")
    @Operation(summary = "Alterar senha", description = "Altera a senha do usuário logado. Exige a senha atual para confirmação.")
    public ResponseEntity<String> alterarSenha(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @Valid @RequestBody AlterarSenhaRequestDTO dto) {

        usuarioService.alterarSenha(usuario.getId(), dto);
        return ResponseEntity.ok("Senha alterada com sucesso.");
    }

    /**
     * Solicita alteração de e-mail. Envia OTP ao novo endereço após validar senha.
     */
    @PostMapping("/me/solicitar-alteracao-email")
    @Operation(summary = "Solicitar alteração de e-mail", description = "Valida a senha atual e envia código de verificação ao novo e-mail.")
    public ResponseEntity<String> solicitarAlteracaoEmail(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @Valid @RequestBody SolicitarAlteracaoEmailRequestDTO dto) {

        alteracaoEmailService.solicitarAlteracao(usuario.getId(), dto);
        return ResponseEntity.ok("Código de verificação enviado para o novo e-mail.");
    }

    /**
     * Confirma a alteração de e-mail com o código OTP recebido.
     */
    @PutMapping("/me/confirmar-alteracao-email")
    @Operation(summary = "Confirmar alteração de e-mail", description = "Valida o código OTP e efetiva a troca de e-mail.")
    public ResponseEntity<String> confirmarAlteracaoEmail(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @Valid @RequestBody ConfirmarAlteracaoEmailRequestDTO dto) {

        alteracaoEmailService.confirmarAlteracao(usuario.getId(), dto);
        return ResponseEntity.ok("E-mail alterado com sucesso. Faça login novamente.");
    }

    @GetMapping("/me/resumo")
    @Operation(summary = "Obter balanço geral", description = "Retorna o balanço consolidado para exibição no perfil.")
    public ResponseEntity<PerfilResumoResponseDTO> obterResumoFinanceiro(
            @AuthenticationPrincipal UsuarioEntity usuario) {

        PerfilResumoResponseDTO resumo = usuarioService.obterResumoFinanceiro(usuario.getId());
        return ResponseEntity.ok(resumo);
    }
}
