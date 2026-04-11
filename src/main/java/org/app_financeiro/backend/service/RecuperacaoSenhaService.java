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

/**
 * Serviço responsável pelo fluxo de recuperação de senha.
 *
 * FLUXO COMPLETO:
 * 1. Usuário solicita recuperação via e-mail
 * 2. Sistema gera um token UUID, salva no banco e envia link por e-mail
 * 3. Usuário clica no link e é redirecionado para o frontend com o token na URL
 * 4. Frontend extrai o token, valida com o backend e exibe formulário de nova senha
 * 5. Usuário envia nova senha + token, backend valida e persiste a troca
 *
 * SEGURANÇA:
 * - Tokens têm validade de 30 minutos
 * - Solicitar novo token invalida automaticamente o anterior
 * - Token é UUID v4 (imprevisível)
 * - Rate Limiting é aplicado no Controller
 */
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

    /**
     * Inicia o processo de recuperação de senha gerando um token único.
     * O processo utiliza throttle de 5 minutos por e-mail para evitar abusos.
     *
     * @param dto dados contendo o e-mail do usuário
     */
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

    /**
     * Valida a integridade e expiração de um token de recuperação.
     *
     * @param token UUID do token a ser validado
     * @return e-mail associado ao token válido
     * @throws RegraDeNegocioException caso o token seja inválido ou esteja expirado
     */
    public String validarToken(String token) {
        TokenRecuperacaoSenhaEntity tokenEntity = tokenRepository.findByTokenAndIsUtilizadoFalse(token)
                .orElseThrow(() -> new RegraDeNegocioException("Token de recuperação inválido ou já utilizado."));

        if (tokenEntity.getDataExpiracao().isBefore(LocalDateTime.now())) {
            log.warn("Token de recuperação expirado para e-mail: {}", tokenEntity.getEmail());
            throw new RegraDeNegocioException("Token expirado. Solicite uma nova recuperação de senha.");
        }

        return tokenEntity.getEmail();
    }

    /**
     * Realiza a alteração da senha do usuário utilizando um token de recuperação válido.
     * Ao resetar, as sessões ativas do usuário são invalidadas para segurança.
     *
     * @param dto dados contendo o token e a nova senha
     * @throws RegraDeNegocioException caso o token seja inválido
     * @throws RecursoNaoEncontradoException caso o usuário não seja localizado
     */
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

    /**
     * Envia o e-mail de recuperação utilizando template HTML e URL base configurada.
     *
     * @param destinatario e-mail do usuário
     * @param token token gerado para o link
     */
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
