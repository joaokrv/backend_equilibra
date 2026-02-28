package org.app_financeiro.backend.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO padronizado para respostas de erro da API.
 * Substitui os Map<String, Object> genéricos por um objeto tipado e consistente.
 *
 * Usado pelo GlobalExceptionHandler para formatar todas as respostas de erro.
 */
@Data
public class ErroResponseDTO {

    private LocalDateTime timestamp;
    private int status;
    private String erro;
    private String mensagem;
    private List<String> detalhes; // Para erros de validação (múltiplos campos)

    /**
     * Construtor para erros simples (uma mensagem).
     */
    public ErroResponseDTO(int status, String erro, String mensagem) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.erro = erro;
        this.mensagem = mensagem;
    }

    /**
     * Construtor para erros de validação (múltiplas mensagens de campo).
     */
    public ErroResponseDTO(int status, String erro, String mensagem, List<String> detalhes) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.erro = erro;
        this.mensagem = mensagem;
        this.detalhes = detalhes;
    }
}
