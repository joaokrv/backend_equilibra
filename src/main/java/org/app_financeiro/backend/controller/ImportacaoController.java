package org.app_financeiro.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.importacao.ConfirmarImportacaoRequestDTO;
import org.app_financeiro.backend.dto.importacao.ImportacaoIniciadaDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.service.ImportacaoService;
import org.app_financeiro.backend.service.ImportacaoService.ImportacaoConfirmadaDTO;
import org.app_financeiro.backend.service.ImportacaoService.ImportacaoDesfeitaDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Importação de transações via documentos (PDF fatura, PDF extrato, CSV). */
@RestController
@RequestMapping("/api/importacao")
@Tag(name = "Importacao", description = "Importação de transações a partir de documentos bancários")
public class ImportacaoController {

    private final ImportacaoService importacaoService;

    public ImportacaoController(ImportacaoService importacaoService) {
        this.importacaoService = importacaoService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Iniciar importação",
               description = "Recebe um documento (PDF ou CSV) e extrai candidatas a transação para revisão.")
    public ResponseEntity<ImportacaoIniciadaDTO> iniciarImportacao(
            @RequestPart("arquivo") MultipartFile arquivo,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(importacaoService.iniciarImportacao(arquivo, usuario.getId()));
    }

    @PostMapping("/confirmar")
    @Operation(summary = "Confirmar importação",
               description = "Confirma as candidatas selecionadas pelo usuário, criando as transações.")
    public ResponseEntity<ImportacaoConfirmadaDTO> confirmarImportacao(
            @Valid @RequestBody ConfirmarImportacaoRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(importacaoService.confirmarImportacao(dto, usuario.getId()));
    }

    @DeleteMapping("/{importacaoId}/transacoes")
    @Operation(summary = "Desfazer importação",
               description = "Reverte todas as transações criadas por esta importação (soft delete + efeitos financeiros).")
    public ResponseEntity<ImportacaoDesfeitaDTO> desfazerImportacao(
            @PathVariable UUID importacaoId,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(importacaoService.desfazerImportacao(importacaoId, usuario.getId()));
    }

    @DeleteMapping("/{importacaoId}")
    @Operation(summary = "Cancelar sessão de importação",
               description = "Cancela uma sessão PENDENTE antes da confirmação.")
    public ResponseEntity<Void> cancelarImportacao(
            @PathVariable UUID importacaoId,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        importacaoService.cancelarImportacao(importacaoId, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
