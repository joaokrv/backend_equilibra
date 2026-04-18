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

import java.util.List;

/** Consulta e pagamento de faturas de cartão. */
@RestController
@RequestMapping("/api/faturas")
public class FaturaController {

    private final FaturaService faturaService;

    public FaturaController(FaturaService faturaService) {
        this.faturaService = faturaService;
    }

    @GetMapping("/cartao/{cartaoId}")
    public ResponseEntity<List<FaturaResponseDTO>> listarFaturasPorCartao(
            @PathVariable Long cartaoId,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        List<FaturaResponseDTO> response = faturaService.listarFaturasPorCartao(cartaoId, usuario.getId());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<FaturaResponseDTO> pagarFatura(
            @PathVariable Long id,
            @Valid @RequestBody PagarFaturaRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        FaturaResponseDTO fatura = faturaService.pagarFatura(id, usuario.getId(), dto);
        
        return ResponseEntity.ok(fatura);
    }
}