package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.TransacaoRecorrenteRequestDTO;
import org.app_financeiro.backend.dto.response.TransacaoRecorrenteResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.service.TransacaoRecorrenteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/recorrentes")
@Tag(name = "TransacoesRecorrentes", description = "Gestão de receitas e despesas fixas")
public class TransacaoRecorrenteController {

    private final TransacaoRecorrenteService service;

    public TransacaoRecorrenteController(TransacaoRecorrenteService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Criar recorrência", description = "Cadastra uma nova receita ou despesa fixa.")
    public ResponseEntity<TransacaoRecorrenteResponseDTO> criar(
            @Valid @RequestBody TransacaoRecorrenteRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(dto, usuario.getId()));
    }

    @GetMapping
    @Operation(summary = "Listar recorrências", description = "Retorna todas as recorrências ativas do usuário.")
    public ResponseEntity<List<TransacaoRecorrenteResponseDTO>> listar(
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(service.listar(usuario.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar recorrência", description = "Edita uma recorrência. Afeta apenas meses futuros.")
    public ResponseEntity<TransacaoRecorrenteResponseDTO> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody TransacaoRecorrenteRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(service.atualizar(id, dto, usuario.getId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativar recorrência", description = "Desativa a recorrência para sempre. Transações já geradas permanecem.")
    public ResponseEntity<Void> deletar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        service.deletar(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "Cancelar mês", description = "Cancela a geração da recorrência para um mês específico.")
    public ResponseEntity<Void> cancelarMes(
            @PathVariable Long id,
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        service.cancelarMes(id, ano, mes, usuario.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/cancelar")
    @Operation(summary = "Reativar mês", description = "Remove o cancelamento de um mês específico.")
    public ResponseEntity<Void> reativarMes(
            @PathVariable Long id,
            @RequestParam int ano,
            @RequestParam int mes,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        service.reativarMes(id, ano, mes, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
