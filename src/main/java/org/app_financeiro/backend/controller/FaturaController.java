package org.app_financeiro.backend.controller;

import org.app_financeiro.backend.dto.request.PagarFaturaRequestDTO;
import org.app_financeiro.backend.dto.response.FaturaResponseDTO;
import org.app_financeiro.backend.service.FaturaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/** Consulta e pagamento de faturas de cartão. */
@RestController
@RequestMapping("/api/faturas")
@Tag(name = "Faturas", description = "Consulta e pagamento de faturas de cartão de crédito")
public class FaturaController {

    private final FaturaService faturaService;

    public FaturaController(FaturaService faturaService) {
        this.faturaService = faturaService;
    }

    @GetMapping("/cartao/{cartaoId}")
    @Operation(summary = "Listar faturas por cartão", description = "Retorna todas as faturas de um cartão com status atualizado em tempo real (ghost closing).")
    public ResponseEntity<List<FaturaResponseDTO>> listarFaturasPorCartao(
            @PathVariable Long cartaoId,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        List<FaturaResponseDTO> response = faturaService.listarFaturasPorCartao(cartaoId, usuario.getId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar fatura por ID", description = "Retorna os detalhes de uma fatura específica com status atualizado (ghost closing).")
    public ResponseEntity<FaturaResponseDTO> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        FaturaResponseDTO fatura = faturaService.buscarFaturaComDetalhe(id, usuario.getId());
        return ResponseEntity.ok(fatura);
    }

    @PostMapping("/{id}/pagar")
    @Operation(summary = "Pagar fatura", description = "Registra um pagamento para uma fatura. O valor é debitado da conta selecionada e atualiza o status se quitada.")
    public ResponseEntity<FaturaResponseDTO> pagarFatura(
            @PathVariable Long id,
            @Valid @RequestBody PagarFaturaRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        FaturaResponseDTO fatura = faturaService.pagarFatura(id, usuario.getId(), dto);

        return ResponseEntity.ok(fatura);
    }
}