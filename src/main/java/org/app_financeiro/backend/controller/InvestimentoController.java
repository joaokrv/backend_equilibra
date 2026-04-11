package org.app_financeiro.backend.controller;

import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.InvestimentoAtualizacaoRequestDTO;
import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.service.InvestimentoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.app_financeiro.backend.entity.UsuarioEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Investimentos", description = "Endpoints para gestão de metas e investimentos de poupança")
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
    @Operation(summary = "Criar investimento", description = "Cria uma nova meta de investimento para o usuário logado.")
    public ResponseEntity<InvestimentoResponseDTO> criarInvestimento(
            @Valid @RequestBody InvestimentoRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        InvestimentoResponseDTO investimento = investimentoService.criarInvestimento(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(investimento);
    }

    /**
     * Lista todos os investimentos ativos do usuário.
     *
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com a lista de investimentos
     */
    @GetMapping
    @Operation(summary = "Listar investimentos", description = "Retorna todos os investimentos ativos do usuário logado.")
    public ResponseEntity<List<InvestimentoResponseDTO>> listarInvestimentos(
            @AuthenticationPrincipal UsuarioEntity usuario) {

        List<InvestimentoResponseDTO> investimentos = investimentoService.buscarTodosDoUsuario(usuario.getId());
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
    @Operation(summary = "Adicionar depósito", description = "Registra um aporte em um investimento, debitando o valor de uma conta bancária.")
    public ResponseEntity<InvestimentoResponseDTO> depositar(
            @PathVariable Long id,
            @RequestParam BigDecimal valor,
            @RequestParam Long contaId,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        InvestimentoResponseDTO investimento = investimentoService.adicionarDeposito(id, valor, contaId, usuario.getId());
        return ResponseEntity.ok(investimento);
    }

    /**
     * Resgata um valor de um investimento para uma conta.
     *
     * @param id        ID do investimento
     * @param valor     Valor do resgate (obrigatório)
     * @param contaId   ID da conta destino (obrigatório)
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com o investimento atualizado
     */
    @PostMapping("/{id}/resgatar")
    @Operation(summary = "Resgatar valor", description = "Retira um valor do investimento e credita em uma conta bancária.")
    public ResponseEntity<InvestimentoResponseDTO> resgatarInvestimento(
            @PathVariable Long id,
            @RequestParam BigDecimal valor,
            @RequestParam Long contaId,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        InvestimentoResponseDTO investimento = investimentoService.resgatarInvestimento(id, valor, contaId, usuario.getId());
        return ResponseEntity.ok(investimento);
    }

    /**
     * Atualiza a meta de um investimento.
     *
     * @param id        ID do investimento
     * @param novaMeta  Novo valor para a meta (obrigatório)
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com o investimento atualizado
     */
    @PutMapping("/{id}/meta")
    @Operation(summary = "Atualizar meta", description = "Altera o valor do objetivo (meta final) de um investimento.")
    public ResponseEntity<InvestimentoResponseDTO> atualizarMeta(
            @PathVariable Long id,
            @RequestParam BigDecimal novaMeta,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        InvestimentoResponseDTO investimento = investimentoService.atualizarMeta(id, novaMeta, usuario.getId());
        return ResponseEntity.ok(investimento);
    }

    /**
     * Atualiza nome da meta, valor da meta e tipo de investimento.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Atualizar investimento", description = "Edita nome, meta e tipo do investimento, incluindo tipo personalizado.")
    public ResponseEntity<InvestimentoResponseDTO> atualizarInvestimento(
            @PathVariable Long id,
            @Valid @RequestBody InvestimentoAtualizacaoRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        InvestimentoResponseDTO investimento = investimentoService.atualizarInvestimento(id, dto, usuario.getId());
        return ResponseEntity.ok(investimento);
    }

    /**
     * Desativa um investimento caso não haja saldo nele.
     *
     * @param id        ID do investimento
     * @param usuarioId ID do usuário (header temporário)
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir investimento", description = "Realiza o soft delete do investimento. Só é permitido se o saldo estiver zerado.")
    public ResponseEntity<Void> deletarInvestimento(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        investimentoService.deletarInvestimento(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
