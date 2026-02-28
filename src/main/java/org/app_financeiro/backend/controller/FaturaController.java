package org.app_financeiro.backend.controller;

import org.app_financeiro.backend.dto.request.PagarFaturaRequestDTO;
import org.app_financeiro.backend.dto.response.FaturaResponseDTO;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.service.FaturaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller responsável pelas operações relacionadas a faturas.
 *
 * Endpoints:
 * - GET    /api/faturas/cartao/{cartaoId} → Lista todas as faturas de um cartão
 * - POST   /api/faturas/{id}/pagar        → Paga uma fatura específica
 *
 * NOTA: O header "UsuarioId" é TEMPORÁRIO — será substituído por JWT/SecurityContext.
 */
@RestController
@RequestMapping("/api/faturas")
public class FaturaController {

    private final FaturaService faturaService;

    public FaturaController(FaturaService faturaService) {
        this.faturaService = faturaService;
    }

    /**
     * Lista todas as faturas de um cartão específico do usuário.
     *
     * @param cartaoId  ID do cartão
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com a lista de faturas do cartão
     */
    @GetMapping("/cartao/{cartaoId}")
    public ResponseEntity<List<FaturaResponseDTO>> listarFaturasPorCartao(
            @PathVariable Long cartaoId,
            @RequestHeader("UsuarioId") Long usuarioId) {

        // O serviço atualmente retorna List<Object>. Convertendo conforme esperado quando implementado.
        // Será necessário adaptar o serviço para retornar FaturaEntity ou FaturaResponseDTO.
        List<?> faturas = faturaService.listarFaturasPorCartao(cartaoId, usuarioId);
        
        List<FaturaResponseDTO> response = faturas.stream()
                .filter(obj -> obj instanceof FaturaEntity)
                .map(obj -> new FaturaResponseDTO((FaturaEntity) obj))
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Paga uma fatura existente, alterando seu status para PAGA.
     *
     * @param id        ID da fatura
     * @param dto       Dados opcionais de pagamento da fatura
     * @param usuarioId ID do usuário (header temporário)
     * @return 200 OK com a fatura atualizada
     */
    @PostMapping("/{id}/pagar")
    public ResponseEntity<FaturaResponseDTO> pagarFatura(
            @PathVariable Long id,
            @RequestBody(required = false) PagarFaturaRequestDTO dto,
            @RequestHeader("UsuarioId") Long usuarioId) {

        Object fatura = faturaService.pagarFatura(id, usuarioId);
        
        if (fatura instanceof FaturaEntity) {
            return ResponseEntity.ok(new FaturaResponseDTO((FaturaEntity) fatura));
        }
        
        // Retorno temporário para caso o serviço retorne nulo antes de ser implementado
        return ResponseEntity.ok().build();
    }
}