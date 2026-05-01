package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.service.RelatorioService;
import org.app_financeiro.backend.dto.request.RelatorioFiltroDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/relatorios")
@Tag(name = "Relatorios", description = "Geração de relatórios financeiros em PDF e CSV")
public class RelatorioController {

    private final RelatorioService relatorioService;

    public RelatorioController(RelatorioService relatorioService) {
        this.relatorioService = relatorioService;
    }

    @PostMapping("/exportar")
    @Operation(summary = "Exportar Relatório", description = "Gera um arquivo (PDF ou CSV) contendo o extrato das transações do usuário baseadas no filtro informado.")
    public ResponseEntity<byte[]> exportarRelatorio(
            @Valid @RequestBody RelatorioFiltroDTO filtro,
            @RequestParam("formato") String formato,
            @AuthenticationPrincipal UsuarioEntity usuarioLogado) {

        if (!formato.equalsIgnoreCase("PDF") && !formato.equalsIgnoreCase("CSV")) {
            throw new IllegalArgumentException("Formato inválido. Os formatos suportados são 'PDF' e 'CSV'.");
        }

        byte[] arquivoBytes = relatorioService.exportarRelatorio(filtro, usuarioLogado.getId(), formato);

        HttpHeaders headers = new HttpHeaders();

        if (formato.equalsIgnoreCase("PDF")) {
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio_financeiro.pdf");
        } else {
            headers.setContentType(MediaType.valueOf("text/csv"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=relatorio_financeiro.csv");
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(arquivoBytes);
    }
}
