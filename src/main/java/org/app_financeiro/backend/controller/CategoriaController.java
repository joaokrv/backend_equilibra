package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.app_financeiro.backend.dto.request.CategoriaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CategoriaResponseDTO;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.service.CategoriaService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CRUD de categorias de transação. */
@RestController
@RequestMapping("/api/categorias")
@Tag(name = "Categorias", description = "CRUD de categorias de transação")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    /** Service valida unicidade por nome+tipo+usuário. */
    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> criarCategoria(
            @Valid @RequestBody CategoriaRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        CategoriaResponseDTO categoria = categoriaService.criarCategoria(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(categoria);
    }

    /** Filtra por tipo (RECEITA/DESPESA) se informado; retorna todas se omitido. */
    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> listarCategorias(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @RequestParam(required = false) TipoTransacao tipo) {

        List<CategoriaResponseDTO> categorias;
        if (tipo != null) {
            categorias = categoriaService.buscarPorTipo(usuario.getId(), tipo);
        } else {
            categorias = categoriaService.buscarTodasDoUsuario(usuario.getId());
        }
        return ResponseEntity.ok(categorias);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> atualizarCategoria(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        CategoriaResponseDTO atualizada = categoriaService.atualizarCategoria(id, dto, usuario.getId());
        return ResponseEntity.ok(atualizada);
    }

    /** Soft delete. Service valida ownership. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarCategoria(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        categoriaService.deletarCategoria(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
