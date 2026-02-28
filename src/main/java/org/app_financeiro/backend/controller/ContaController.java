package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.ContaResponseDTO;
import org.app_financeiro.backend.service.ContaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller responsável pelo CRUD de contas bancárias.
 *
 * Endpoints:
 * - POST   /api/contas         → Cria uma nova conta
 * - GET    /api/contas         → Lista todas as contas do usuário
 * - GET    /api/contas/{id}    → Busca conta por ID
 * - DELETE /api/contas/{id}    → Soft delete da conta
 *
 * NOTA IMPORTANTE:
 * O header "UsuarioId" é TEMPORÁRIO. Quando Spring Security + JWT forem
 * implementados, o ID do usuário virá do token de autenticação (SecurityContext).
 * Por enquanto, o frontend deve enviar esse header manualmente para identificar o usuário.
 */
@RestController
@RequestMapping("/api/contas")
public class ContaController {

    private final ContaService contaService;

    public ContaController(ContaService contaService) {
        this.contaService = contaService;
    }

    /**
     * Cria uma nova conta bancária para o usuário.
     * Se o saldo não for informado, o Service deve assumir R$ 0,00.
     *
     * @param dto   dados da conta (nome, saldo opcional)
     * @param usuarioId ID do usuário (header temporário)
     * @return 201 Created com a conta criada
     */
    @PostMapping
    public ResponseEntity<ContaResponseDTO> criarConta(
            @Valid @RequestBody ContaRegistroRequestDTO dto,
            @RequestHeader("UsuarioId") Long usuarioId) {

        ContaResponseDTO conta = contaService.criarConta(dto, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(conta);
    }

    /**
     * Lista todas as contas ativas do usuário.
     *
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com a lista de contas
     */
    @GetMapping
    public ResponseEntity<List<ContaResponseDTO>> listarContas(
            @RequestHeader("UsuarioId") Long usuarioId) {

        List<ContaResponseDTO> contas = contaService.buscarTodasDoUsuario(usuarioId);
        return ResponseEntity.ok(contas);
    }

    /**
     * Busca uma conta específica por ID.
     * O Service deve validar que a conta pertence ao usuário.
     *
     * @param id        ID da conta
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com a conta encontrada
     */
    @GetMapping("/{id}")
    public ResponseEntity<ContaResponseDTO> buscarPorId(
            @PathVariable Long id,
            @RequestHeader("UsuarioId") Long usuarioId) {

        ContaResponseDTO conta = contaService.buscarPorId(id, usuarioId);
        return ResponseEntity.ok(conta);
    }

    /**
     * Desativa (soft delete) uma conta.
     * O Service deve validar que a conta pertence ao usuário.
     *
     * @param id        ID da conta
     * @param usuarioId ID do usuário (header temporário)
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarConta(
            @PathVariable Long id,
            @RequestHeader("UsuarioId") Long usuarioId) {

        contaService.deletarConta(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}
