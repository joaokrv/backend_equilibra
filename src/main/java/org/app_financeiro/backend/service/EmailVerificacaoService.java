package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ReenviarCodigoRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
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
import java.time.LocalDateTime;

/** Fluxo de verificação de e-mail via OTP de 6 dígitos (15 min). Vincula código por e-mail, não por FK, pois o usuário ainda não está autenticado nesta etapa. */
@Service
public class EmailVerificacaoService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificacaoService.class);
    private static final int MAX_TENTATIVAS_OTP = 5;
    private static final int MAX_TENTATIVAS_CODIGO = 3;

    private final CodigoVerificacaoRepository codigoVerificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ExternalEmailSenderService externalEmailSenderService;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificacaoService(CodigoVerificacaoRepository codigoVerificacaoRepository,
                                   UsuarioRepository usuarioRepository,
                                   ExternalEmailSenderService externalEmailSenderService) {
        this.codigoVerificacaoRepository = codigoVerificacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.externalEmailSenderService = externalEmailSenderService;
    }

    @Transactional
    public void gerarCodigo(String email) {
        int numero = secureRandom.nextInt(1_000_000);
        String codigo = String.format("%06d", numero);

        CodigoVerificacaoEntity codigoEntity = new CodigoVerificacaoEntity();
        codigoEntity.setEmail(email);
        codigoEntity.setCodigo(codigo);
        codigoEntity.setDataExpiracao(LocalDateTime.now().plusMinutes(15));
        codigoEntity.setUtilizado(false);

        codigoVerificacaoRepository.save(codigoEntity);
        log.info("Código de verificação gerado para e-mail {}", email);

        enviarEmail(email, codigo);
    }

    @Transactional
    public void verificarEmail(VerificarEmailRequestDTO dto) {
        validarCodigoSimples(dto.email(), dto.codigo());

        UsuarioEntity usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        usuario.setEmailVerificado(true);
        usuarioRepository.save(usuario);
        log.info("E-mail {} verificado com sucesso", dto.email());
    }

    /**
     * Valida o código OTP sem realizar atualizações na entidade de Usuário.
     * Útil para o fluxo de pré-registro.
     */
    @Transactional
    public void validarCodigoSimples(String email, String codigo) {
        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository
                .findTopByEmailAndIsUtilizadoFalseOrderByDataCriacaoDesc(email)
                .orElseThrow(() -> new CodigoVerificacaoInvalidoException("Nenhum código ativo. Solicite um novo código."));

        if (codigoEntity.getDataExpiracao().isBefore(LocalDateTime.now())) {
            log.warn("Código de verificação expirado para e-mail {}", email);
            throw new CodigoVerificacaoInvalidoException("Código expirado. Solicite um novo código.");
        }

        if (!codigoEntity.getCodigo().equals(codigo)) {
            log.warn("Código inválido para e-mail {}", email);
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
            log.warn("Tentativa de reenviar código para e-mail já verificado: {}", dto.email());
            throw new RegraDeNegocioException("Este e-mail já foi verificado");
        }

        codigoVerificacaoRepository.invalidarTodosPendentes(dto.email());

        gerarCodigo(dto.email());
    }

    private void enviarEmail(String destinatario, String codigo) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/verificacao-email.html");
            String htmlTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String htmlContent = htmlTemplate.replace("{{CODIGO}}", codigo);

            externalEmailSenderService.sendHtml(destinatario, "Equilibra - Codigo de Verificacao", htmlContent);
            log.info("E-mail de verificação enviado para {}", destinatario);

        } catch (IOException e) {
            log.warn("Falha ao montar template de verificação para {}: {}", destinatario, e.getMessage());
        }
    }
}
