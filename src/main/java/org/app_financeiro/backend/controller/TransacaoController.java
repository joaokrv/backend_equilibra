package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller responsável pelo CRUD de transações financeiras.
 *
 * Endpoints:
 * - POST   /api/transacoes                     → Cria uma nova transação
 * - GET    /api/transacoes?ano=2026&mes=2       → Lista transações do mês
 * - PUT    /api/transacoes/{id}                 → Atualiza uma transação existente
 * - DELETE /api/transacoes/{id}                 → Soft delete da transação
 *
 * NOTA: O header "UsuarioId" é TEMPORÁRIO — será substituído por JWT/SecurityContext.
 *
 * REGRAS DE NEGÓCIO IMPORTANTES (implementadas no Service):
 * - DESPESA com conta: debita o saldo da conta (bloqueia se saldo insuficiente)
 * - DESPESA com cartão: consome o limite (bloqueia se limite insuficiente)
 * - RECEITA com conta: credita o saldo da conta
 * - Ao atualizar/deletar, o Service deve reverter o impacto anterior antes de aplicar o novo
 */
@RestController
@RequestMapping("/api/transacoes")
public class TransacaoController {

    private final TransacaoService transacaoService;

    public TransacaoController(TransacaoService transacaoService) {
        this.transacaoService = transacaoService;
    }

    /**
     * Cria uma nova transação.
     * O Service é responsável por impactar o saldo/limite conforme o tipo.
     *
     * @param dto       dados da transação (descricao, valor, data, tipo, contaId/cartaoId, categoriaId, etc.)
     * @param usuarioId ID do usuário (header temporário)
     * @return 201 Created com a transação criada
     */
    @PostMapping
    public ResponseEntity<TransacaoResponseDTO> criarTransacao(
            @Valid @RequestBody TransacaoRegistroRequestDTO dto,
            @RequestHeader("UsuarioId") Long usuarioId) {

        TransacaoResponseDTO transacao = transacaoService.criarTransacao(dto, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(transacao);
    }

    /**
     * Lista transações do usuário filtradas por mês e ano.
     * Ambos os parâmetros são obrigatórios.
     *
     * @param ano       ano da consulta (ex: 2026)
     * @param mes       mês da consulta (1-12)
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com a lista de transações do mês
     */
    @GetMapping
    public ResponseEntity<List<TransacaoResponseDTO>> listarPorMes(
            @RequestParam int ano,
            @RequestParam int mes,
            @RequestHeader("UsuarioId") Long usuarioId) {

        List<TransacaoResponseDTO> transacoes = transacaoService.buscarPorMes(ano, mes, usuarioId);
        return ResponseEntity.ok(transacoes);
    }

    /**
     * Atualiza uma transação existente.
     * O Service deve reverter o impacto anterior e aplicar o novo.
     *
     * @param id        ID da transação
     * @param dto       novos dados da transação
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com a transação atualizada
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransacaoResponseDTO> atualizarTransacao(
            @PathVariable Long id,
            @Valid @RequestBody TransacaoRegistroRequestDTO dto,
            @RequestHeader("UsuarioId") Long usuarioId) {

        TransacaoResponseDTO transacao = transacaoService.atualizarTransacao(id, dto, usuarioId);
        return ResponseEntity.ok(transacao);
    }

    /**
     * Desativa (soft delete) uma transação.
     * O Service deve reverter o impacto no saldo/limite.
     *
     * @param id        ID da transação
     * @param usuarioId ID do usuário (header temporário)
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTransacao(
            @PathVariable Long id,
            @RequestHeader("UsuarioId") Long usuarioId) {

        transacaoService.deletarTransacao(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}
