package org.app_financeiro.backend.exception;

import org.app_financeiro.backend.dto.response.ErroResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

/**
 * Interceptador global de exceções.
 * Captura as exceções lançadas pelos Services e formata uma resposta JSON
 * padronizada (ErroResponseDTO) para o frontend.
 *
 * Hierarquia de exceptions tratadas:
 *
 *   RuntimeException
 *   ├── RecursoNaoEncontradoException .............. 404 NOT_FOUND
 *   └── RegraDeNegocioException .................... 400 BAD_REQUEST
 *       ├── EmailJaCadastradoException ............. 409 CONFLICT
 *       ├── CredenciaisInvalidasException .......... 401 UNAUTHORIZED
 *       ├── EmailNaoVerificadoException ............ 403 FORBIDDEN
 *       ├── SaldoInsuficienteException ............. 422 UNPROCESSABLE_ENTITY
 *       ├── LimiteInsuficienteException ............ 422 UNPROCESSABLE_ENTITY
 *       ├── OperacaoNaoPermitidaException .......... 409 CONFLICT
 *       └── CodigoVerificacaoInvalidoException ..... 400 BAD_REQUEST
 *
 *   MethodArgumentNotValidException ................ 422 UNPROCESSABLE_ENTITY
 *   HttpMessageNotReadableException ................ 400 BAD_REQUEST
 *   Exception (fallback) ........................... 500 INTERNAL_SERVER_ERROR
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    // =============================================
    // EXCEPTIONS ESPECÍFICAS (mais específica primeiro)
    // =============================================

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ErroResponseDTO> handleEmailJaCadastrado(EmailJaCadastradoException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.CONFLICT.value(),
                "Conflito",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResponseDTO> handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "Não autorizado",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EmailNaoVerificadoException.class)
    public ResponseEntity<ErroResponseDTO> handleEmailNaoVerificado(EmailNaoVerificadoException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.FORBIDDEN.value(),
                "Acesso negado",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<ErroResponseDTO> handleSaldoInsuficiente(SaldoInsuficienteException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Saldo insuficiente",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(LimiteInsuficienteException.class)
    public ResponseEntity<ErroResponseDTO> handleLimiteInsuficiente(LimiteInsuficienteException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Limite insuficiente",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(OperacaoNaoPermitidaException.class)
    public ResponseEntity<ErroResponseDTO> handleOperacaoNaoPermitida(OperacaoNaoPermitidaException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.CONFLICT.value(),
                "Operação não permitida",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.CONFLICT);
    }

    // =============================================
    // EXCEPTIONS GENÉRICAS (base)
    // =============================================

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponseDTO> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.NOT_FOUND.value(),
                "Recurso não encontrado",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponseDTO> handleRegraDeNegocio(RegraDeNegocioException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de regra de negócio",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.BAD_REQUEST);
    }

    // =============================================
    // EXCEPTIONS DO SPRING (validação, JSON malformado)
    // =============================================

    /**
     * Trata erros de validação do @Valid nos DTOs.
     * Extrai as mensagens de cada campo que falhou e retorna uma lista.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponseDTO> handleValidacao(MethodArgumentNotValidException ex) {
        List<String> detalhes = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();

        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Erro de validação",
                "Um ou mais campos estão inválidos. Verifique os detalhes.",
                detalhes
        );
        return new ResponseEntity<>(erro, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Trata JSON malformado ou campos com tipo errado (ex: enviar texto onde espera número).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponseDTO> handleJsonMalformado(HttpMessageNotReadableException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição inválida",
                "O corpo da requisição está malformado ou contém valores com tipo incorreto."
        );
        return new ResponseEntity<>(erro, HttpStatus.BAD_REQUEST);
    }

    // =============================================
    // FALLBACK (qualquer exceção não tratada)
    // =============================================

    /**
     * Captura qualquer exceção inesperada que não foi tratada acima.
     * Retorna 500 sem expor detalhes internos (segurança).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponseDTO> handleExcecaoGenerica(Exception ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erro interno",
                "Ocorreu um erro inesperado. Tente novamente mais tarde."
        );
        return new ResponseEntity<>(erro, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
