package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.springframework.validation.annotation.Validated;
import org.app_financeiro.backend.dto.response.TransacaoResponseDTO;
import org.app_financeiro.backend.service.TransacaoService;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

/** CRUD de transações financeiras. */
@Validated
@RestController
@RequestMapping("/api/transacoes")
@Tag(name = "Transacoes", description = "Gerenciamento de receitas e despesas (contas e cartões)")
public class TransacaoController {

    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    @PostMapping
    @Operation(summary = "Criar transação", description = "Registra uma nova receita ou despesa. Impacta automaticamente o saldo da conta ou limite do cartão.")
    public ResponseEntity<TransacaoResponseDTO> criarTransacao(
            @Valid @RequestBody TransacaoRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        TransacaoResponseDTO transacao = transacaoService.criarTransacao(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(transacao);
    }

    @GetMapping("/mensal")
    @Operation(summary = "Listar transações mensais", operationId = "listarMensal", description = "Retorna todas as transações de um mês e ano específicos para o usuário logado.")
    public ResponseEntity<List<TransacaoResponseDTO>> listarMensal(
            @RequestParam @Min(2000) @Max(2100) int ano,
            @RequestParam @Min(1) @Max(12) int mes,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        List<TransacaoResponseDTO> transacoes = transacaoService.buscarPorMes(ano, mes, usuario.getId());
        return ResponseEntity.ok(transacoes);
    }

    @GetMapping("/fatura")
    @Operation(summary = "Listar transações por fatura", operationId = "listarPorFatura", description = "Retorna todas as transações de uma fatura específica.")
    public ResponseEntity<List<TransacaoResponseDTO>> listarPorFatura(
            @RequestParam Long faturaId,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        List<TransacaoResponseDTO> transacoes = transacaoService.buscarPorFatura(faturaId, usuario.getId());
        return ResponseEntity.ok(transacoes);
    }

    @GetMapping("/intervalo")
    @Operation(summary = "Listar transacoes por intervalo",
               description = "Retorna transacoes entre dataInicio e dataFim. Intervalo maximo de 12 meses.")
    public ResponseEntity<List<TransacaoResponseDTO>> listarPorIntervalo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        List<TransacaoResponseDTO> transacoes =
                transacaoService.listarPorIntervalo(dataInicio, dataFim, usuario.getId());
        return ResponseEntity.ok(transacoes);
    }

    /** Service reverte impacto anterior e aplica o novo antes de salvar. */
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transação", description = "Altera os dados de uma transação existente e reajusta os saldos/limites impactados.")
    public ResponseEntity<TransacaoResponseDTO> atualizarTransacao(
            @PathVariable Long id,
            @Valid @RequestBody TransacaoRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        TransacaoResponseDTO transacao = transacaoService.atualizarTransacao(id, dto, usuario.getId());
        return ResponseEntity.ok(transacao);
    }

    @GetMapping
    @Operation(summary = "Listar transações paginadas", operationId = "listarPaginado", description = "Retorna transações do usuário em páginas (sem filtro mensal). Use parâmetros page, size, sort.")
    public ResponseEntity<org.springframework.data.domain.Page<TransacaoResponseDTO>> listarPaginado(
            @AuthenticationPrincipal UsuarioEntity usuario,
            org.springframework.data.domain.Pageable pageable) {

        var page = transacaoService.listarPorUsuario(usuario.getId(), pageable);
        return ResponseEntity.ok(page);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir transação", description = "Remove logicamente uma transação e estorna seu impacto financeiro (saldo/limite). Use grupo=true para excluir todas as parcelas de uma compra parcelada.")
    public ResponseEntity<Void> deletarTransacao(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean grupo,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        transacaoService.deletarTransacao(id, usuario.getId(), grupo);
        return ResponseEntity.noContent().build();
    }
}
