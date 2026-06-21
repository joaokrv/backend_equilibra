package org.app_financeiro.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.app_financeiro.backend.dto.response.PatrimonioEvolucaoResponseDTO;
import org.app_financeiro.backend.entity.PatrimonioHistoricoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.service.PatrimonioHistoricoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller de consultas de patrimônio consolidado do usuário.
 */
@RestController
@RequestMapping("/api/patrimonio")
@RequiredArgsConstructor
@Validated
@Tag(name = "Patrimonio", description = "Endpoints para histórico patrimonial")
public class PatrimonioController {

    private final PatrimonioHistoricoService patrimonioHistoricoService;

    @GetMapping("/evolucao")
    @Operation(summary = "Evolução patrimonial", description = "Retorna snapshots diários de patrimônio (contas + investimentos) no período informado.")
    public ResponseEntity<List<PatrimonioEvolucaoResponseDTO>> listarEvolucao(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @RequestParam(defaultValue = "180") @Min(1) @Max(1825) int dias) {

        List<PatrimonioHistoricoEntity> historico = patrimonioHistoricoService.buscarEvolucao(usuario.getId(), dias);
        List<PatrimonioEvolucaoResponseDTO> response = historico.stream()
                .map(item -> new PatrimonioEvolucaoResponseDTO(item.getDataReferencia(), item.getValorTotal()))
                .toList();

        return ResponseEntity.ok(response);
    }
}