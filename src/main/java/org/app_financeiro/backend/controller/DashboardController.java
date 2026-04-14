package org.app_financeiro.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.app_financeiro.backend.dto.response.DashboardResumoPeriodoResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.PeriodoDashboard;
import org.app_financeiro.backend.service.DashboardResumoService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints consolidados para dados da dashboard.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Resumo financeiro para a tela inicial")
public class DashboardController {

    private final DashboardResumoService dashboardResumoService;

    public DashboardController(DashboardResumoService dashboardResumoService) {
        this.dashboardResumoService = dashboardResumoService;
    }

    @GetMapping("/resumo")
    @Operation(
            summary = "Resumo por periodo",
            description = "Retorna resumo da dashboard para 1M, 3M, 6M ou 1A, comparando com a janela anterior de mesmo tamanho."
    )
    public ResponseEntity<DashboardResumoPeriodoResponseDTO> obterResumoPorPeriodo(
            @AuthenticationPrincipal UsuarioEntity usuario,
            @RequestParam(defaultValue = "1M") String periodo) {

        PeriodoDashboard periodoSelecionado = PeriodoDashboard.fromCodigo(periodo);
        DashboardResumoPeriodoResponseDTO resumo =
                dashboardResumoService.obterResumoPorPeriodo(usuario.getId(), periodoSelecionado);

        return ResponseEntity.ok(resumo);
    }
}
