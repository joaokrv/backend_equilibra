package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.app_financeiro.backend.dto.request.CartaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CartaoResponseDTO;
import org.app_financeiro.backend.service.CartaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CRUD de cartões de crédito. */
@RestController
@RequestMapping("/api/cartoes")
@Tag(name = "Cartoes", description = "CRUD de cartões de crédito")
public class CartaoController {

    private final CartaoService cartaoService;

    public CartaoController(CartaoService cartaoService) {
        this.cartaoService = cartaoService;
    }

    @PostMapping
    public ResponseEntity<CartaoResponseDTO> criarCartao(
            @Valid @RequestBody CartaoRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        CartaoResponseDTO cartao = cartaoService.criarCartao(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(cartao);
    }

    @GetMapping
    public ResponseEntity<List<CartaoResponseDTO>> listarCartoes(
            @AuthenticationPrincipal UsuarioEntity usuario) {

        List<CartaoResponseDTO> cartoes = cartaoService.buscarTodosDoUsuario(usuario.getId());
        return ResponseEntity.ok(cartoes);
    }

    /** Service valida ownership. */
    @GetMapping("/{id}")
    public ResponseEntity<CartaoResponseDTO> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        CartaoResponseDTO cartao = cartaoService.buscarPorId(id, usuario.getId());
        return ResponseEntity.ok(cartao);
    }

    /** Soft delete. Service valida ownership. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarCartao(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        cartaoService.deletarCartao(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }

    /** Service valida ownership e aplica regras de limite. */
    @PutMapping("/{id}")
    public ResponseEntity<CartaoResponseDTO> atualizarCartao(
            @PathVariable Long id,
            @Valid @RequestBody CartaoRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        CartaoResponseDTO cartaoAtualizado = cartaoService.atualizarCartao(id, dto, usuario.getId());
        return ResponseEntity.ok(cartaoAtualizado);
    }
}
