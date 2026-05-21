package org.app_financeiro.backend.exception;

import org.app_financeiro.backend.dto.response.ErroResponseDTO;
import org.app_financeiro.backend.enums.ErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import jakarta.validation.ConstraintViolationException;

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
 *   └── RegraDeNegocioException .................... 422 UNPROCESSABLE_ENTITY
 *       │   (requisição válida, mas viola regra de negócio do domínio)
 *       ├── EmailJaCadastradoException ............. 409 CONFLICT
 *       ├── CredenciaisInvalidasException .......... 401 UNAUTHORIZED
 *       ├── EmailNaoVerificadoException ............ 403 FORBIDDEN
 *       ├── SaldoInsuficienteException ............. 422 UNPROCESSABLE_ENTITY
 *       ├── LimiteInsuficienteException ............ 422 UNPROCESSABLE_ENTITY
 *       ├── OperacaoNaoPermitidaException .......... 409 CONFLICT
 *       └── CodigoVerificacaoInvalidoException ..... 400 BAD_REQUEST
 *   RateLimitExcedidoException ..................... 429 TOO_MANY_REQUESTS
 *
 *   MethodArgumentNotValidException ................ 422 UNPROCESSABLE_ENTITY
 *   ConstraintViolationException ................... 400 BAD_REQUEST
 *   MissingServletRequestParameterException ........ 400 BAD_REQUEST
 *   HttpMessageNotReadableException ................ 400 BAD_REQUEST
 *   ObjectOptimisticLockingFailureException ........ 409 CONFLICT
 *   DataIntegrityViolationException ................ 409 CONFLICT
 *   Exception (fallback) ........................... 500 INTERNAL_SERVER_ERROR
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }


    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ErroResponseDTO> handleEmailJaCadastrado(EmailJaCadastradoException ex) {
        String msg = messageSource.getMessage(ErrorCode.EMAIL_JA_CADASTRADO.getMessageKey(), null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.CONFLICT.value(),
                ErrorCode.EMAIL_JA_CADASTRADO.name(),
                "Conflito",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResponseDTO> handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        String msg = messageSource.getMessage(ErrorCode.CREDENCIAIS_INVALIDAS.getMessageKey(), null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                ErrorCode.CREDENCIAIS_INVALIDAS.name(),
                "Não autorizado",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(EmailNaoVerificadoException.class)
    public ResponseEntity<ErroResponseDTO> handleEmailNaoVerificado(EmailNaoVerificadoException ex) {
        String msg = messageSource.getMessage(ErrorCode.EMAIL_NAO_VERIFICADO.getMessageKey(), null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.FORBIDDEN.value(),
                ErrorCode.EMAIL_NAO_VERIFICADO.name(),
                "Acesso negado",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<ErroResponseDTO> handleSaldoInsuficiente(SaldoInsuficienteException ex) {
        String msg = messageSource.getMessage(ErrorCode.SALDO_INSUFICIENTE.getMessageKey(), null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ErrorCode.SALDO_INSUFICIENTE.name(),
                "Saldo insuficiente",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(LimiteInsuficienteException.class)
    public ResponseEntity<ErroResponseDTO> handleLimiteInsuficiente(LimiteInsuficienteException ex) {
        String msg = messageSource.getMessage(ErrorCode.LIMITE_INSUFICIENTE.getMessageKey(), null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ErrorCode.LIMITE_INSUFICIENTE.name(),
                "Limite insuficiente",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(OperacaoNaoPermitidaException.class)
    public ResponseEntity<ErroResponseDTO> handleOperacaoNaoPermitida(OperacaoNaoPermitidaException ex) {
        String msg = messageSource.getMessage(ErrorCode.OPERACAO_NAO_PERMITIDA.getMessageKey(), null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.CONFLICT.value(),
                ErrorCode.OPERACAO_NAO_PERMITIDA.name(),
                "Operação não permitida",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.CONFLICT);
    }


    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponseDTO> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        String msg = messageSource.getMessage(ErrorCode.REGISTRO_NAO_ENCONTRADO.getMessageKey(), null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.NOT_FOUND.value(),
                ErrorCode.REGISTRO_NAO_ENCONTRADO.name(),
                "Recurso não encontrado",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RateLimitExcedidoException.class)
    public ResponseEntity<ErroResponseDTO> handleRateLimitExcedido(RateLimitExcedidoException ex) {
        String msg = messageSource.getMessage(ErrorCode.RATE_LIMIT_EXCEEDED.getMessageKey(), null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ErrorCode.RATE_LIMIT_EXCEEDED.name(),
                "Muitas requisições",
                msg
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(erro);
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponseDTO> handleRegraDeNegocio(RegraDeNegocioException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ErrorCode.REGRA_DE_NEGOCIO.name(),
                "Erro de regra de negócio",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.UNPROCESSABLE_ENTITY);
    }


    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResponseDTO> handleBadCredentials(BadCredentialsException ex) {
        String msg = messageSource.getMessage("error.bad_credentials", null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                ErrorCode.CREDENCIAIS_INVALIDAS.name(),
                "Não autorizado",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErroResponseDTO> handleDisabled(DisabledException ex) {
        String msg = messageSource.getMessage("error.conta_desativada", null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.FORBIDDEN.value(),
                "ACCESS_DENIED",
                "Acesso negado",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErroResponseDTO> handleAuthentication(AuthenticationException ex) {
        String msg = messageSource.getMessage("error.autenticacao_generica", null, LocaleContextHolder.getLocale());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.UNAUTHORIZED.value(),
                "AUTHENTICATION_ERROR",
                "Erro de autenticação",
                msg
        );
        return new ResponseEntity<>(erro, HttpStatus.UNAUTHORIZED);
    }


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
                "VALIDATION_ERROR",
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
                "INVALID_REQUEST",
                "Requisição inválida",
                "O corpo da requisição está malformado ou contém valores com tipo incorreto."
        );
        return new ResponseEntity<>(erro, HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata erros de parâmetros com valores inválidos (ex: IllegalArgumentException no Controller de Relatórios).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponseDTO> handleIllegalArgument(IllegalArgumentException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PARAMETER_VALUE",
                "Valor Incorreto",
                ex.getMessage()
        );
        return new ResponseEntity<>(erro, HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata falhas de infraestrutura no envio de e-mail (SMTP indisponível, timeout, etc.).
     */
    @ExceptionHandler(MailException.class)
    public ResponseEntity<ErroResponseDTO> handleMailException(MailException ex) {
        log.error("Falha ao enviar e-mail: {}", ex.getMessage(), ex);
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "MAIL_SERVICE_UNAVAILABLE",
                "Serviço de e-mail indisponível",
                "Não foi possível enviar o e-mail agora. Tente novamente em alguns minutos."
        );
        return new ResponseEntity<>(erro, HttpStatus.SERVICE_UNAVAILABLE);
    }


    /**
     * Trata violações de constraint de bean validation (@PathVariable, @RequestParam).
     * Complementa o handleValidacao que trata apenas @RequestBody.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErroResponseDTO> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> detalhes = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Parâmetro inválido",
                "Um ou mais parâmetros violam as restrições definidas.",
                detalhes
        );
        return new ResponseEntity<>(erro, HttpStatus.BAD_REQUEST);
    }

    /**
     * Trata conflito de versão em atualização concorrente (@Version / locking otimista).
     * Ocorre quando dois requests atualizam a mesma entidade ao mesmo tempo.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErroResponseDTO> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.CONFLICT.value(),
                "CONCURRENT_UPDATE",
                "Conflito de atualização",
                "Operação conflitante. Tente novamente."
        );
        return new ResponseEntity<>(erro, HttpStatus.CONFLICT);
    }

    /**
     * Trata violações de integridade referencial ou unique constraint no banco.
     * Evita vazar stack trace do Hibernate — retorna mensagem genérica.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponseDTO> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade de dados: {}", ex.getMostSpecificCause().getMessage());
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.CONFLICT.value(),
                "DATA_CONFLICT",
                "Conflito de dados",
                "Conflito de dados. Verifique se o registro já existe ou está em uso."
        );
        return new ResponseEntity<>(erro, HttpStatus.CONFLICT);
    }

    /**
     * Trata parâmetros de query obrigatórios ausentes (ex: ?ano= sem valor).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResponseDTO> handleMissingParam(MissingServletRequestParameterException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.BAD_REQUEST.value(),
                "MISSING_PARAMETER",
                "Parâmetro ausente",
                "Parâmetro obrigatório ausente: '" + ex.getParameterName() + "' (tipo: " + ex.getParameterType() + ")."
        );
        return new ResponseEntity<>(erro, HttpStatus.BAD_REQUEST);
    }


    /**
     * Trata métodos HTTP não suportados em rotas permitAll() que chegariam ao Dispatcher e dariam 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResponseDTO> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "METHOD_NOT_ALLOWED",
                "Método HTTP não permitido",
                "O método " + ex.getMethod() + " não é suportado para este endpoint."
        );
        return new ResponseEntity<>(erro, HttpStatus.METHOD_NOT_ALLOWED);
    }

    /**
     * Captura qualquer exceção inesperada que não foi tratada acima.
     * Retorna 500 sem expor detalhes internos (segurança).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponseDTO> handleExcecaoGenerica(Exception ex) {
        log.error("Exceção inesperada capturada: {}", ex.getMessage(), ex);
        ErroResponseDTO erro = new ErroResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "Erro interno",
                "Ocorreu um erro inesperado. Tente novamente mais tarde."
        );
        return new ResponseEntity<>(erro, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
