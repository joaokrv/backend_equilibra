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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * Serviço responsável pelo fluxo de verificação de e-mail via código OTP.
 *
 * FLUXO COMPLETO:
 * 1. Usuário se registra via UsuarioService.registrarUsuario()
 * 2. O controller chama gerarCodigo(email) — cria um OTP de 6 dígitos com validade de 15 minutos
 * 3. O código é enviado por e-mail via JavaMailSender usando template HTML
 * 4. Usuário envia email + código via verificarEmail() — se válido, marca emailVerificado = true
 * 5. Se o código expirou ou foi perdido, o usuário pode chamar reenviarCodigo()
 *
 * DECISÕES TÉCNICAS:
 * - O vínculo entre código e usuário é feito pelo campo "email" (String), não por @ManyToOne.
 *   Isso simplifica o fluxo pré-login (o usuário ainda não está autenticado quando verifica).
 * - Códigos antigos NÃO são deletados — são apenas marcados como utilizado = true.
 *   Isso preserva o histórico e é consistente com o padrão de soft delete do projeto.
 */
@Service
public class EmailVerificacaoService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificacaoService.class);

    private final CodigoVerificacaoRepository codigoVerificacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.username}")
    private String mailFrom;

    public EmailVerificacaoService(CodigoVerificacaoRepository codigoVerificacaoRepository,
                                   UsuarioRepository usuarioRepository,
                                   JavaMailSender mailSender) {
        this.codigoVerificacaoRepository = codigoVerificacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.mailSender = mailSender;
    }

    /**
     * Gera um código de verificação de 6 dígitos para o e-mail informado.
     * O código é persistido no banco com validade de 15 minutos e enviado por e-mail.
     *
     * @param email endereço de e-mail do usuário
     */
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

    /**
     * Valida um código de verificação recebido pelo usuário.
     * Caso o código seja legítimo, o e-mail do usuário é marcado como verificado.
     *
     * @param dto objeto contendo e-mail e código enviado
     * @throws CodigoVerificacaoInvalidoException caso o código seja inválido ou expirado
     * @throws RecursoNaoEncontradoException caso o usuário não seja localizado no sistema
     */
    @Transactional
    public void verificarEmail(VerificarEmailRequestDTO dto) {
        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository
                .findByEmailAndCodigoAndIsUtilizadoFalse(dto.email(), dto.codigo())
                .orElseThrow(() -> new CodigoVerificacaoInvalidoException("Código inválido ou já utilizado"));

        if (codigoEntity.getDataExpiracao().isBefore(LocalDateTime.now())) {
            log.warn("Código de verificação expirado para e-mail {}", dto.email());
            throw new CodigoVerificacaoInvalidoException("Código expirado. Solicite um novo código.");
        }

        codigoEntity.setUtilizado(true);

        UsuarioEntity usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        usuario.setEmailVerificado(true);

        codigoVerificacaoRepository.save(codigoEntity);
        usuarioRepository.save(usuario);
        log.info("E-mail {} verificado com sucesso", dto.email());
    }

    /**
     * Invalida códigos anteriores e gera uma nova solicitação de verificação para o usuário.
     *
     * @param dto objeto contendo o e-mail para reenvio
     * @throws RecursoNaoEncontradoException caso o usuário não exista
     * @throws RegraDeNegocioException caso o e-mail já tenha sido verificado previamente
     */
    @Transactional
    public void reenviarCodigo(ReenviarCodigoRequestDTO dto) {
        UsuarioEntity usuario = usuarioRepository.findByEmail(dto.email())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado para este e-mail"));

        if (usuario.isEmailVerificado()) {
            log.warn("Tentativa de reenviar código para e-mail já verificado: {}", dto.email());
            throw new RegraDeNegocioException("Este e-mail já foi verificado");
        }

        codigoVerificacaoRepository.findTopByEmailAndIsUtilizadoFalseOrderByDataCriacaoDesc(dto.email())
                .ifPresent(codigoAntigo -> {
                    codigoAntigo.setUtilizado(true);
                    codigoVerificacaoRepository.save(codigoAntigo);
                });

        gerarCodigo(dto.email());
    }

    /**
     * Constrói e envia a mensagem de e-mail utilizando template HTML.
     *
     * @param destinatario endereço de e-mail do destinatário
     * @param codigo código OTP de 6 dígitos
     */
    private void enviarEmail(String destinatario, String codigo) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/verificacao-email.html");
            String htmlTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String htmlContent = htmlTemplate.replace("{{CODIGO}}", codigo);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(mailFrom, "Equilibra", "UTF-8"));
            helper.setTo(destinatario);
            helper.setSubject("Equilibra - Codigo de Verificacao");
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("E-mail de verificação enviado para {}", destinatario);

        } catch (MessagingException | IOException e) {
            log.warn("Falha ao enviar e-mail de verificação para {} — o usuário pode solicitar reenvio: {}", destinatario, e.getMessage());
        }
    }
}
