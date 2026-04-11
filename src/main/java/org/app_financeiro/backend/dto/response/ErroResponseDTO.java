package org.app_financeiro.backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO padronizado para respostas de erro da API.
 * Substitui os Map<String, Object> genéricos por um objeto tipado e consistente.
 *
 * Usado pelo GlobalExceptionHandler para formatar todas as respostas de erro.
 */
public record ErroResponseDTO(
    LocalDateTime timestamp,
    int status,
    String code,
    String erro,
    String mensagem,
    List<String> detalhes
) {
    public ErroResponseDTO(int status, String code, String erro, String mensagem) {
        this(LocalDateTime.now(), status, code, erro, mensagem, null);
    }

    /**
     * Construtor para erros de validação (múltiplas mensagens de campo).
     */
    public ErroResponseDTO(int status, String code, String erro, String mensagem, List<String> detalhes) {
        this(LocalDateTime.now(), status, code, erro, mensagem, detalhes);
    }
}
