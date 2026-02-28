package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.service.InvestimentoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller responsável pelo CRUD de investimentos (metas/poupanças).
 *
 * Endpoints:
 * - POST /api/investimentos                              → Cria um novo investimento
 * - GET  /api/investimentos                              → Lista todos os investimentos do usuário
 * - POST /api/investimentos/{id}/depositar?valor=X&contaId=Y → Deposita valor no investimento (debita da conta)
 *
 * NOTA: O header "UsuarioId" é TEMPORÁRIO — será substituído por JWT/SecurityContext.
 *
 * REGRAS DE NEGÓCIO IMPORTANTES (implementadas no Service):
 * - O depósito DEBITA o saldo da conta de origem (bloqueia se saldo insuficiente)
 * - O depósito INCREMENTA o valorAtual do investimento
 * - O valorInicial é definido na criação e também debita da conta (se contaId for informada)
 */
@RestController
@RequestMapping("/api/investimentos")
public class InvestimentoController {

    private final InvestimentoService investimentoService;

    public InvestimentoController(InvestimentoService investimentoService) {
        this.investimentoService = investimentoService;
    }

    /**
     * Cria um novo investimento/meta de poupança para o usuário.
     *
     * @param dto       dados do investimento (descricao, valorInicial, meta)
     * @param usuarioId ID do usuário (header temporário)
     * @return 201 Created com o investimento criado
     */
    @PostMapping
    public ResponseEntity<InvestimentoResponseDTO> criarInvestimento(
            @Valid @RequestBody InvestimentoRegistroRequestDTO dto,
            @RequestHeader("UsuarioId") Long usuarioId) {

        InvestimentoResponseDTO investimento = investimentoService.criarInvestimento(dto, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(investimento);
    }

    /**
     * Lista todos os investimentos ativos do usuário.
     *
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com a lista de investimentos
     */
    @GetMapping
    public ResponseEntity<List<InvestimentoResponseDTO>> listarInvestimentos(
            @RequestHeader("UsuarioId") Long usuarioId) {

        List<InvestimentoResponseDTO> investimentos = investimentoService.buscarTodosDoUsuario(usuarioId);
        return ResponseEntity.ok(investimentos);
    }

    /**
     * Deposita um valor em um investimento existente.
     * O valor é debitado da conta de origem (contaId).
     *
     * @param id        ID do investimento
     * @param valor     valor a depositar
     * @param contaId   ID da conta de onde o dinheiro sai
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com o investimento atualizado
     */
    @PostMapping("/{id}/depositar")
    public ResponseEntity<InvestimentoResponseDTO> depositar(
            @PathVariable Long id,
            @RequestParam BigDecimal valor,
            @RequestParam Long contaId,
            @RequestHeader("UsuarioId") Long usuarioId) {

        InvestimentoResponseDTO investimento = investimentoService.adicionarDeposito(id, valor, contaId, usuarioId);
        return ResponseEntity.ok(investimento);
    }
}
