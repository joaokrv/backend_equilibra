package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.CartaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CartaoResponseDTO;
import org.app_financeiro.backend.service.CartaoService;
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
 * Controller responsável pelo CRUD de cartões de crédito.
 *
 * Endpoints:
 * - POST   /api/cartoes         → Cria um novo cartão
 * - GET    /api/cartoes         → Lista todos os cartões do usuário
 * - GET    /api/cartoes/{id}    → Busca cartão por ID
 * - DELETE /api/cartoes/{id}    → Soft delete do cartão
 *
 * NOTA: O header "UsuarioId" é TEMPORÁRIO — será substituído por JWT/SecurityContext.
 */
@RestController
@RequestMapping("/api/cartoes")
public class CartaoController {

    private final CartaoService cartaoService;

    public CartaoController(CartaoService cartaoService) {
        this.cartaoService = cartaoService;
    }

    /**
     * Cria um novo cartão de crédito para o usuário.
     *
     * @param dto       dados do cartão (nome, limite, diaFechamento, diaVencimento)
     * @param usuarioId ID do usuário (header temporário)
     * @return 201 Created com o cartão criado
     */
    @PostMapping
    public ResponseEntity<CartaoResponseDTO> criarCartao(
            @Valid @RequestBody CartaoRegistroRequestDTO dto,
            @RequestHeader("UsuarioId") Long usuarioId) {

        CartaoResponseDTO cartao = cartaoService.criarCartao(dto, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(cartao);
    }

    /**
     * Lista todos os cartões ativos do usuário.
     *
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com a lista de cartões
     */
    @GetMapping
    public ResponseEntity<List<CartaoResponseDTO>> listarCartoes(
            @RequestHeader("UsuarioId") Long usuarioId) {

        List<CartaoResponseDTO> cartoes = cartaoService.buscarTodosDoUsuario(usuarioId);
        return ResponseEntity.ok(cartoes);
    }

    /**
     * Busca um cartão específico por ID.
     * O Service deve validar que o cartão pertence ao usuário.
     *
     * @param id        ID do cartão
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com o cartão encontrado
     */
    @GetMapping("/{id}")
    public ResponseEntity<CartaoResponseDTO> buscarPorId(
            @PathVariable Long id,
            @RequestHeader("UsuarioId") Long usuarioId) {

        CartaoResponseDTO cartao = cartaoService.buscarPorId(id, usuarioId);
        return ResponseEntity.ok(cartao);
    }

    /**
     * Desativa (soft delete) um cartão.
     * O Service deve validar que o cartão pertence ao usuário.
     *
     * @param id        ID do cartão
     * @param usuarioId ID do usuário (header temporário)
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarCartao(
            @PathVariable Long id,
            @RequestHeader("UsuarioId") Long usuarioId) {

        cartaoService.deletarCartao(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}
