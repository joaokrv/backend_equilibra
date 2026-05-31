package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ReenviarCodigoRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoCodigoVerificacao;
import org.app_financeiro.backend.exception.CodigoVerificacaoInvalidoException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Fluxo de verificação de e-mail via OTP de 6 dígitos (15 min). Vincula código por e-mail, não por FK, pois o usuário ainda não está autenticado nesta etapa. */
@Service
public class EmailVerificacaoService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificacaoService.class);
    private static final int MAX_TENTATIVAS_OTP = 5;
    private static final ZoneId FUSO_BRASIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HORARIO_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HORARIO_COMPLETO_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final Duration AVISO_TENTATIVA_THROTTLE = Duration.ofHours(1);
    private static final long AVISO_CLEANUP_INTERVAL = 200;

    private final CodigoVerificacaoRepository codigoVerificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ExternalEmailSenderService externalEmailSenderService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Instant> ultimoAvisoTentativaPorEmail = new ConcurrentHashMap<>();
    private final AtomicLong avisoCallCounter = new AtomicLong(0);

    public EmailVerificacaoService(CodigoVerificacaoRepository codigoVerificacaoRepository,
                                   UsuarioRepository usuarioRepository,
                                   ExternalEmailSenderService externalEmailSenderService) {
        this.codigoVerificacaoRepository = codigoVerificacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.externalEmailSenderService = externalEmailSenderService;
    }

    @Transactional
    public void gerarCodigo(String email) {
        gerarCodigo(email, TipoCodigoVerificacao.VERIFICACAO_EMAIL);
    }

    public void gerarCodigo(String email, TipoCodigoVerificacao tipo) {
        int numero = secureRandom.nextInt(1_000_000);
        String codigo = String.format("%06d", numero);
        LocalDateTime expiracao = LocalDateTime.now().plusMinutes(15);

        CodigoVerificacaoEntity codigoEntity = new CodigoVerificacaoEntity();
        codigoEntity.setEmail(email);
        codigoEntity.setCodigo(codigo);
        codigoEntity.setDataExpiracao(expiracao);
        codigoEntity.setTipo(tipo);
        codigoEntity.setUtilizado(false);

        codigoVerificacaoRepository.save(codigoEntity);
        log.info("Código de verificação gerado para e-mail {}", org.app_financeiro.backend.util.EmailMasker.mascarar(email));

        enviarEmail(email, codigo, expiracao, tipo);
    }

    @Transactional(noRollbackFor = CodigoVerificacaoInvalidoException.class)
    public void verificarEmail(VerificarEmailRequestDTO dto) {
        validarCodigoSimples(dto.email(), dto.codigo(), TipoCodigoVerificacao.VERIFICACAO_EMAIL);

        UsuarioEntity usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        usuario.setEmailVerificado(true);
        usuarioRepository.save(usuario);
        log.info("E-mail {} verificado com sucesso", org.app_financeiro.backend.util.EmailMasker.mascarar(dto.email()));
    }

    /**
     * Valida o código OTP sem realizar atualizações na entidade de Usuário.
     * Útil para o fluxo de pré-registro.
     */
    public void validarCodigoSimples(String email, String codigo, TipoCodigoVerificacao tipo) {
        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository
                .findTopByEmailAndTipoAndIsUtilizadoFalseOrderByDataCriacaoDesc(email, tipo)
                .orElseThrow(() -> new CodigoVerificacaoInvalidoException("Nenhum código ativo. Solicite um novo código."));

        if (codigoEntity.getTentativasFalhas() >= MAX_TENTATIVAS_OTP) {
            codigoEntity.setUtilizado(true);
            codigoVerificacaoRepository.save(codigoEntity);
            throw new CodigoVerificacaoInvalidoException("Código bloqueado após múltiplas tentativas. Solicite um novo código.");
        }

        if (codigoEntity.getDataExpiracao().isBefore(LocalDateTime.now())) {
            log.warn("Código de verificação expirado para e-mail {}", org.app_financeiro.backend.util.EmailMasker.mascarar(email));
            throw new CodigoVerificacaoInvalidoException("Código expirado. Solicite um novo código.");
        }

        if (!java.security.MessageDigest.isEqual(
                codigoEntity.getCodigo().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                codigo == null ? new byte[0] : codigo.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            int tentativas = codigoEntity.getTentativasFalhas() + 1;
            codigoEntity.setTentativasFalhas(tentativas);
            if (tentativas >= MAX_TENTATIVAS_OTP) {
                codigoEntity.setUtilizado(true);
            }
            codigoVerificacaoRepository.save(codigoEntity);
            throw new CodigoVerificacaoInvalidoException("Código inválido.");
        }

        codigoEntity.setUtilizado(true);
        codigoVerificacaoRepository.save(codigoEntity);
    }

    @Transactional
    public void reenviarCodigo(ReenviarCodigoRequestDTO dto) {
        UsuarioEntity usuario = usuarioRepository.findByEmail(dto.email()).orElse(null);

        if (usuario == null) {
            log.warn("Reenvio solicitado para e-mail não cadastrado (silenciado)");
            return;
        }

        if (usuario.isEmailVerificado()) {
            log.warn("Tentativa de reenviar código para e-mail já verificado: {}", org.app_financeiro.backend.util.EmailMasker.mascarar(dto.email()));
            throw new RegraDeNegocioException("error.email.ja_verificado", "Este e-mail já foi verificado");
        }

        codigoVerificacaoRepository.invalidarTodosPendentesPorTipo(dto.email(), TipoCodigoVerificacao.VERIFICACAO_EMAIL);
        gerarCodigo(dto.email(), TipoCodigoVerificacao.VERIFICACAO_EMAIL);
    }

    /**
     * Envia aviso ao dono do e-mail quando alguém tenta criar conta com um endereço já cadastrado.
     * Throttle de 1 hora por e-mail para evitar uso como vetor de spam.
     * Nunca lança — falhas são logadas e silenciadas (não devem afetar a resposta fake do pré-registro).
     */
    public void enviarAvisoTentativaCadastro(String email, String nome) {
        Instant agora = Instant.now();
        Instant anterior = ultimoAvisoTentativaPorEmail.get(email);
        if (anterior != null && Duration.between(anterior, agora).compareTo(AVISO_TENTATIVA_THROTTLE) < 0) {
            log.debug("Aviso de tentativa de cadastro suprimido (throttle) para {}",
                    org.app_financeiro.backend.util.EmailMasker.mascarar(email));
            return;
        }
        ultimoAvisoTentativaPorEmail.put(email, agora);
        limparAvisosAntigos(agora);

        try {
            ClassPathResource resource = new ClassPathResource("templates/aviso-tentativa-cadastro.html");
            String htmlTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            String horario = LocalDateTime.now().atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(FUSO_BRASIL)
                    .format(HORARIO_COMPLETO_FORMATTER) + " (UTC-3)";
            String saudacao = (nome != null && !nome.isBlank()) ? ", " + nome.trim().split("\\s+")[0] : "";

            String htmlContent = htmlTemplate
                    .replace("{{HORARIO_TENTATIVA}}", horario)
                    .replace("{{NOME_SAUDACAO}}", saudacao);

            externalEmailSenderService.sendHtml(email,
                    "Tentativa de criação de conta — Equilibra",
                    htmlContent);
            log.info("Aviso de tentativa de cadastro enviado para {}",
                    org.app_financeiro.backend.util.EmailMasker.mascarar(email));
        } catch (IOException | RuntimeException e) {
            log.warn("Falha ao enviar aviso de tentativa de cadastro: {}", e.getMessage());
        }
    }

    /** Cleanup leve a cada N invocações: remove entries com mais de 2× o intervalo de throttle. */
    private void limparAvisosAntigos(Instant agora) {
        long count = avisoCallCounter.incrementAndGet();
        if (count % AVISO_CLEANUP_INTERVAL != 0) {
            return;
        }
        Instant limite = agora.minus(AVISO_TENTATIVA_THROTTLE.multipliedBy(2));
        ultimoAvisoTentativaPorEmail.entrySet().removeIf(entry -> entry.getValue().isBefore(limite));
    }

    private void enviarEmail(String destinatario, String codigo, LocalDateTime expiracao, TipoCodigoVerificacao tipo) {
        try {
            String templateName = templatePorTipo(tipo);
            ClassPathResource resource = new ClassPathResource(templateName);
            String htmlTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String horarioExpiracao = expiracao.atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(FUSO_BRASIL)
                    .format(HORARIO_FORMATTER) + " (UTC-3)";
            
            String htmlContent = htmlTemplate
                    .replace("{{HORARIO_EXPIRACAO}}", horarioExpiracao);
                    
            if (codigo != null && codigo.length() == 6) {
                htmlContent = htmlContent
                    .replace("{{CODIGO_1}}", String.valueOf(codigo.charAt(0)))
                    .replace("{{CODIGO_2}}", String.valueOf(codigo.charAt(1)))
                    .replace("{{CODIGO_3}}", String.valueOf(codigo.charAt(2)))
                    .replace("{{CODIGO_4}}", String.valueOf(codigo.charAt(3)))
                    .replace("{{CODIGO_5}}", String.valueOf(codigo.charAt(4)))
                    .replace("{{CODIGO_6}}", String.valueOf(codigo.charAt(5)));
            }

            externalEmailSenderService.sendHtml(destinatario, assuntoPorTipo(tipo), htmlContent);
            log.info("E-mail de verificação enviado para {}", destinatario);

        } catch (IOException e) {
            log.warn("Falha ao montar template de verificação para {}: {}", destinatario, e.getMessage());
        }
    }

    private String assuntoPorTipo(TipoCodigoVerificacao tipo) {
        return switch (tipo) {
            case EXCLUSAO_CONTA -> "Confirme a exclusão da sua conta — Equilibra";
            case DESATIVACAO_CONTA -> "Confirme a desativação da sua conta — Equilibra";
            case REATIVACAO_CONTA -> "Reative sua conta — Equilibra";
            case VERIFICACAO_EMAIL -> "Código de Verificação — Equilibra";
        };
    }

    private String templatePorTipo(TipoCodigoVerificacao tipo) {
        return switch (tipo) {
            case EXCLUSAO_CONTA -> "templates/exclusao-conta-email.html";
            case DESATIVACAO_CONTA -> "templates/desativacao-conta-email.html";
            case REATIVACAO_CONTA -> "templates/reativacao-conta-email.html";
            case VERIFICACAO_EMAIL -> "templates/verificacao-email.html";
        };
    }
}
