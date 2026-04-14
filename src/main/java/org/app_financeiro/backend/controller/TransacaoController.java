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

/**
 * Controller responsável pelo CRUD de transações financeiras.
 *
 * Endpoints:
 * - POST   /api/transacoes                     → Cria uma nova transação
 * - GET    /api/transacoes?ano=2026&mes=2       → Lista transações do mês
 * - PUT    /api/transacoes/{id}                 → Atualiza uma transação existente
 * - DELETE /api/transacoes/{id}                 → Soft delete da transação
 *
 * REGRAS DE NEGÓCIO IMPORTANTES (implementadas no Service):
 * - DESPESA com conta: debita o saldo da conta (bloqueia se saldo insuficiente)
 * - DESPESA com cartão: consome o limite (bloqueia se limite insuficiente)
 * - RECEITA com conta: credita o saldo da conta
 * - Ao atualizar/deletar, o Service reverter o impacto anterior antes de aplicar o novo
 */
@Validated
@RestController
@RequestMapping("/api/transacoes")
@Tag(name = "Transações", description = "Gerenciamento de receitas e despesas (contas e cartões)")
public class TransacaoController {

    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    /**
     * Cria uma nova transação.
     * O Service é responsável por impactar o saldo/limite conforme o tipo.
     *
     * @param dto     dados da transação (descricao, valor, data, tipo, contaId/cartaoId, categoriaId, etc.)
     * @param usuario usuário autenticado via JWT
     * @return 201 Created com a transação criada
     */
    @PostMapping
    @Operation(summary = "Criar transação", description = "Registra uma nova receita ou despesa. Impacta automaticamente o saldo da conta ou limite do cartão.")
    public ResponseEntity<TransacaoResponseDTO> criarTransacao(
            @Valid @RequestBody TransacaoRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        TransacaoResponseDTO transacao = transacaoService.criarTransacao(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(transacao);
    }

    /**
     * Lista transações do usuário filtradas por mês e ano.
     * Ambos os parâmetros são obrigatórios.
     *
     * @param ano     ano da consulta (ex: 2026)
     * @param mes     mês da consulta (1-12)
     * @param usuario usuário autenticado via JWT
     * @return 200 OK com a lista de transações do mês
     */
    @GetMapping(params = {"ano","mes"})
    @Operation(summary = "Listar transações mensais", description = "Retorna todas as transações de um mês e ano específicos para o usuário logado.")
    public ResponseEntity<List<TransacaoResponseDTO>> listarMensal(
            @RequestParam @Min(2000) @Max(2100) int ano,
            @RequestParam @Min(1) @Max(12) int mes,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        List<TransacaoResponseDTO> transacoes = transacaoService.buscarPorMes(ano, mes, usuario.getId());
        return ResponseEntity.ok(transacoes);
    }

    /**
     * Lista transacoes do usuario em um intervalo de datas.
     */
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

    /**
     * Atualiza uma transação existente.
     * O Service reverte o impacto anterior e aplica o novo.
     *
     * @param id      ID da transação
     * @param dto     novos dados da transação
     * @param usuario usuário autenticado via JWT
     * @return 200 OK com a transação atualizada
     */
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar transação", description = "Altera os dados de uma transação existente e reajusta os saldos/limites impactados.")
    public ResponseEntity<TransacaoResponseDTO> atualizarTransacao(
            @PathVariable Long id,
            @Valid @RequestBody TransacaoRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        TransacaoResponseDTO transacao = transacaoService.atualizarTransacao(id, dto, usuario.getId());
        return ResponseEntity.ok(transacao);
    }

    /**
     * Lista transações do usuário com paginação.
     *
     * @param usuario  usuário autenticado via JWT
     * @param pageable parâmetros de paginação
     * @return 200 OK com a página de transações
     */
    @GetMapping
    @Operation(summary = "Listar transações paginadas", description = "Retorna transações do usuário em páginas (sem filtro mensal). Use parâmetros page, size, sort.")
    public ResponseEntity<org.springframework.data.domain.Page<TransacaoResponseDTO>> listarPaginado(
            @AuthenticationPrincipal UsuarioEntity usuario,
            org.springframework.data.domain.Pageable pageable) {

        var page = transacaoService.listarPorUsuario(usuario.getId(), pageable);
        return ResponseEntity.ok(page);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir transação", description = "Remove logicamente uma transação e estorna seu impacto financeiro (saldo/limite).")
    public ResponseEntity<Void> deletarTransacao(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        transacaoService.deletarTransacao(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
