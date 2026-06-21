package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.ContaResponseDTO;
import org.app_financeiro.backend.service.ContaService;
import org.app_financeiro.backend.service.InvestimentoService;
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

import java.math.BigDecimal;

import java.util.List;

/** CRUD de contas bancárias. */
@RestController
@RequestMapping("/api/contas")
@Tag(name = "Contas", description = "CRUD de contas bancárias")
public class ContaController {

    private final ContaService contaService;
    private final InvestimentoService investimentoService;

    public ContaController(ContaService contaService, InvestimentoService investimentoService) {
        this.contaService = contaService;
        this.investimentoService = investimentoService;
    }

    /**
     * Saldo inicial padrão: R$ 0,00 se não informado. Quando {@code investimentoInicial > 0},
     * a conta e o investimento inicial são criados atomicamente (ou ambos, ou nenhum).
     */
    @PostMapping
    public ResponseEntity<ContaResponseDTO> criarConta(
            @Valid @RequestBody ContaRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        ContaResponseDTO conta = (dto.investimentoInicial() != null
                && dto.investimentoInicial().compareTo(BigDecimal.ZERO) > 0)
                ? investimentoService.criarContaComInvestimentoInicial(dto, usuario.getId())
                : contaService.criarConta(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(conta);
    }

    @GetMapping
    public ResponseEntity<List<ContaResponseDTO>> listarContas(
            @AuthenticationPrincipal UsuarioEntity usuario) {

        List<ContaResponseDTO> contas = contaService.buscarTodasDoUsuario(usuario.getId());
        return ResponseEntity.ok(contas);
    }

    /** Service valida ownership. */
    @GetMapping("/{id}")
    public ResponseEntity<ContaResponseDTO> buscarPorId(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        ContaResponseDTO conta = contaService.buscarPorId(id, usuario.getId());
        return ResponseEntity.ok(conta);
    }

    /** Soft delete. Service valida ownership. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarConta(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        contaService.deletarConta(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/saldo")
    public ResponseEntity<ContaResponseDTO> atualizarSaldo(
            @PathVariable Long id,
            @RequestParam BigDecimal valor,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        ContaResponseDTO conta = contaService.atualizarSaldo(id, valor, usuario.getId());
        return ResponseEntity.ok(conta);
    }
}
