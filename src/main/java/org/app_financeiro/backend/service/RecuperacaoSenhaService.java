package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ResetarSenhaRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarRecuperacaoSenhaRequestDTO;
import org.app_financeiro.backend.entity.TokenRecuperacaoSenhaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.repository.TokenRecuperacaoSenhaRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/** Fluxo de recuperação de senha via token UUID (30 min) enviado por e-mail. */
@Service
public class RecuperacaoSenhaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final int MINUTOS_EXPIRACAO = 30;

    private final TokenRecuperacaoSenhaRepository tokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ExternalEmailSenderService externalEmailSenderService;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    public RecuperacaoSenhaService(TokenRecuperacaoSenhaRepository tokenRepository,
                                    UsuarioRepository usuarioRepository,
                                    PasswordEncoder passwordEncoder,
                                    ExternalEmailSenderService externalEmailSenderService) {
        this.tokenRepository = tokenRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.externalEmailSenderService = externalEmailSenderService;
    }

    @Transactional
    public void solicitarRecuperacao(SolicitarRecuperacaoSenhaRequestDTO dto) {
        var usuarioOpt = usuarioRepository.findByEmail(dto.email());

        if (usuarioOpt.isEmpty()) {
            log.warn("Tentativa de recuperação de senha para e-mail não cadastrado: {}", dto.email());
            return;
        }

        var ultimoTokenOpt = tokenRepository.findFirstByEmailOrderByDataCriacaoDesc(dto.email());
        if (ultimoTokenOpt.isPresent()) {
            var dataCriacao = ultimoTokenOpt.get().getDataCriacao();
            var agora = LocalDateTime.now();
            if (Duration.between(dataCriacao, agora).toMinutes() < 5) {
                log.warn("Solicitação de recuperação ignorada por throttle (limite de 5 min) para e-mail: {}", dto.email());
                return;
            }
        }

        tokenRepository.invalidarTokensAnteriores(dto.email());

        String token = UUID.randomUUID().toString();
        TokenRecuperacaoSenhaEntity tokenEntity = new TokenRecuperacaoSenhaEntity();
        tokenEntity.setEmail(dto.email());
        tokenEntity.setToken(token);
        tokenEntity.setDataExpiracao(LocalDateTime.now().plusMinutes(MINUTOS_EXPIRACAO));
        tokenEntity.setUtilizado(false);

        tokenRepository.save(tokenEntity);
        log.info("Token de recuperação de senha gerado para e-mail: {}", dto.email());

        enviarEmailRecuperacao(dto.email(), token);
    }

    public String validarToken(String token) {
        TokenRecuperacaoSenhaEntity tokenEntity = tokenRepository.findByTokenAndIsUtilizadoFalse(token)
                .orElseThrow(() -> new RegraDeNegocioException("Token de recuperação inválido ou já utilizado."));

        if (tokenEntity.getDataExpiracao().isBefore(LocalDateTime.now())) {
            log.warn("Token de recuperação expirado para e-mail: {}", tokenEntity.getEmail());
            throw new RegraDeNegocioException("Token expirado. Solicite uma nova recuperação de senha.");
        }

        return tokenEntity.getEmail();
    }

    /** Invalida sessões ativas ao limpar chaveSessao após reset de senha. */
    @Transactional
    public void resetarSenha(ResetarSenhaRequestDTO dto) {
        TokenRecuperacaoSenhaEntity tokenEntity = tokenRepository.findByTokenAndIsUtilizadoFalse(dto.token())
                .orElseThrow(() -> new RegraDeNegocioException("Token de recuperação inválido ou já utilizado."));

        if (tokenEntity.getDataExpiracao().isBefore(LocalDateTime.now())) {
            throw new RegraDeNegocioException("Token expirado. Solicite uma nova recuperação de senha.");
        }

        UsuarioEntity usuario = usuarioRepository.findByEmail(tokenEntity.getEmail())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));

        usuario.setSenha(passwordEncoder.encode(dto.novaSenha()));
        usuario.setChaveSessao(null);
        tokenEntity.setUtilizado(true);

        usuarioRepository.save(usuario);
        tokenRepository.save(tokenEntity);

        log.info("Senha resetada com sucesso para e-mail: {}", tokenEntity.getEmail());
    }

    private void enviarEmailRecuperacao(String destinatario, String token) {
        try {
            if (allowedOrigins == null) {
                log.error("Configuração de CORS ausente — e-mail de recuperação não enviado.");
                return;
            }

            String frontendUrl = allowedOrigins.split(",")[0].trim();
            if (frontendUrl.isEmpty()) {
                log.error("cors.allowed-origins está vazio — não é possível gerar link de recuperação.");
                return;
            }

            String linkRecuperacao = frontendUrl + "/reset-password?token=" + token;

            ClassPathResource resource = new ClassPathResource("templates/recuperacao-senha.html");
            String htmlTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String htmlContent = htmlTemplate
                    .replace("{{LINK}}", linkRecuperacao)
                    .replace("{{MINUTOS}}", String.valueOf(MINUTOS_EXPIRACAO));

            externalEmailSenderService.sendHtml(destinatario, "Equilibra - Recuperacao de Senha", htmlContent);
            log.debug("E-mail de recuperação de senha enviado para: {}", destinatario);

        } catch (IOException e) {
            log.warn("Falha ao enviar e-mail de recuperação para {} — {}", destinatario, e.getMessage());
        }
    }
}
