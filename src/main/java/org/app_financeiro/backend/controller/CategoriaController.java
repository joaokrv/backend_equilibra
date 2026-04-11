package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
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

/**
 * Controller responsável pelo CRUD de categorias de transação.
 *
 * Endpoints:
 * - POST   /api/categorias          → Cria uma nova categoria
 * - GET    /api/categorias          → Lista todas as categorias do usuário
 * - GET    /api/categorias?tipo=... → Filtra categorias por tipo (RECEITA/DESPESA)
 * - DELETE /api/categorias/{id}     → Soft delete da categoria
 *
 * NOTA: O header "UsuarioId" é TEMPORÁRIO — será substituído por JWT/SecurityContext.
 */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    /**
     * Cria uma nova categoria para o usuário.
     * O Service deve validar que não existe outra categoria com mesmo nome e tipo para este usuário.
     *
     * @param dto       dados da categoria (nome, tipo RECEITA/DESPESA)
     * @param usuarioId ID do usuário (header temporário)
     * @return 201 Created com a categoria criada
     */
    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> criarCategoria(
            @Valid @RequestBody CategoriaRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        CategoriaResponseDTO categoria = categoriaService.criarCategoria(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(categoria);
    }

    /**
     * Lista categorias do usuário. Se o parâmetro "tipo" for informado,
     * filtra por RECEITA ou DESPESA. Caso contrário, retorna todas.
     *
     * @param usuarioId ID do usuário (header temporário)
     * @param tipo      filtro opcional: RECEITA ou DESPESA
     * @return 200 OK com a lista de categorias
     */
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

    /**
     * Atualiza o nome de uma categoria existente.
     *
     * @param id      ID da categoria
     * @param dto     Dados com o novo nome
     * @param usuario Usuário autenticado via JWT
     * @return 200 OK com a categoria atualizada
     */
    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> atualizarCategoria(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        CategoriaResponseDTO atualizada = categoriaService.atualizarCategoria(id, dto, usuario.getId());
        return ResponseEntity.ok(atualizada);
    }

    /**
     * Desativa (soft delete) uma categoria.
     * O Service deve validar que a categoria pertence ao usuário.
     *
     * @param id        ID da categoria
     * @param usuarioId ID do usuário (header temporário)
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarCategoria(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        categoriaService.deletarCategoria(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
