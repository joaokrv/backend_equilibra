package org.app_financeiro.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.app_financeiro.backend.dto.request.InvestimentoAtualizacaoRequestDTO;
import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.MovimentacaoAtualizacaoRequestDTO;
import org.app_financeiro.backend.dto.request.RendimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.dto.response.MovimentacaoInvestimentoResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoMovimentacaoInvestimento;
import org.app_financeiro.backend.service.InvestimentoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/investimentos")
@Tag(name = "Investimentos", description = "Gestão de investimentos, metas e extrato de movimentações")
public class InvestimentoController {

    private final InvestimentoService investimentoService;

    public InvestimentoController(InvestimentoService investimentoService) {
        this.investimentoService = investimentoService;
    }

    @PostMapping
    @Operation(summary = "Criar investimento")
    public ResponseEntity<InvestimentoResponseDTO> criarInvestimento(
            @Valid @RequestBody InvestimentoRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investimentoService.criarInvestimento(dto, usuario.getId()));
    }

    @GetMapping
    @Operation(summary = "Listar investimentos")
    public ResponseEntity<List<InvestimentoResponseDTO>> listarInvestimentos(
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(investimentoService.buscarTodosDoUsuario(usuario.getId()));
    }

    @PostMapping("/{id}/depositar")
    @Operation(summary = "Adicionar aporte")
    public ResponseEntity<InvestimentoResponseDTO> depositar(
            @PathVariable Long id,
            @RequestParam BigDecimal valor,
            @RequestParam Long contaId,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(investimentoService.adicionarDeposito(id, valor, contaId, usuario.getId()));
    }

    @PostMapping("/{id}/resgatar")
    @Operation(summary = "Resgatar valor")
    public ResponseEntity<InvestimentoResponseDTO> resgatarInvestimento(
            @PathVariable Long id,
            @RequestParam BigDecimal valor,
            @RequestParam Long contaId,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(investimentoService.resgatarInvestimento(id, valor, contaId, usuario.getId()));
    }

    @PutMapping("/{id}/meta")
    @Operation(summary = "Atualizar meta")
    public ResponseEntity<InvestimentoResponseDTO> atualizarMeta(
            @PathVariable Long id,
            @RequestParam BigDecimal novaMeta,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(investimentoService.atualizarMeta(id, novaMeta, usuario.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar investimento")
    public ResponseEntity<InvestimentoResponseDTO> atualizarInvestimento(
            @PathVariable Long id,
            @Valid @RequestBody InvestimentoAtualizacaoRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(investimentoService.atualizarInvestimento(id, dto, usuario.getId()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir investimento")
    public ResponseEntity<Void> deletarInvestimento(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        investimentoService.deletarInvestimento(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rendimento")
    @Operation(summary = "Registrar rendimento", description = "Registra um rendimento (positivo ou negativo) em um investimento. Não movimenta conta bancária.")
    public ResponseEntity<MovimentacaoInvestimentoResponseDTO> registrarRendimento(
            @Valid @RequestBody RendimentoRegistroRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investimentoService.registrarRendimento(dto, usuario.getId()));
    }

    @GetMapping("/movimentacoes")
    @Operation(summary = "Extrato de movimentações",
            description = "Retorna movimentações paginadas. Filtro padrão: últimos 30 dias. Tamanhos: 10, 20, 50.")
    public ResponseEntity<Page<MovimentacaoInvestimentoResponseDTO>> listarMovimentacoes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoMovimentacaoInvestimento tipo,
            @RequestParam(required = false) Long investimentoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UsuarioEntity usuario) {

        int pageSize = (size == 20 || size == 50) ? size : 10;
        PageRequest pageable = PageRequest.of(page, pageSize, Sort.by("data").descending());

        return ResponseEntity.ok(
                investimentoService.listarMovimentacoes(
                        usuario.getId(), tipo, investimentoId, dataInicio, dataFim, pageable));
    }

    @GetMapping("/movimentacoes/preview")
    @Operation(summary = "Preview de movimentações", description = "Retorna as últimas 5 movimentações de todos os investimentos do usuário.")
    public ResponseEntity<List<MovimentacaoInvestimentoResponseDTO>> buscarPreview(
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(investimentoService.buscarPreview(usuario.getId()));
    }

    @PutMapping("/movimentacoes/{movId}")
    @Operation(summary = "Editar movimentação",
            description = "Edita qualquer tipo de movimentação. Para APORTE/RESGATE, reverte o efeito anterior e recria. contaId obrigatório para APORTE/RESGATE.")
    public ResponseEntity<MovimentacaoInvestimentoResponseDTO> editarMovimentacao(
            @PathVariable Long movId,
            @Valid @RequestBody MovimentacaoAtualizacaoRequestDTO dto,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        return ResponseEntity.ok(investimentoService.editarMovimentacao(movId, dto, usuario.getId()));
    }

    @DeleteMapping("/movimentacoes/{movId}")
    @Operation(summary = "Excluir movimentação",
            description = "Soft delete com reversão completa do efeito financeiro (saldo de conta e valorAtual do investimento).")
    public ResponseEntity<Void> excluirMovimentacao(
            @PathVariable Long movId,
            @AuthenticationPrincipal UsuarioEntity usuario) {
        investimentoService.excluirMovimentacao(movId, usuario.getId());
        return ResponseEntity.noContent().build();
    }
}
