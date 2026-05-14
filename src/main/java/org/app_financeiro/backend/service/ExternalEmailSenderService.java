package org.app_financeiro.backend.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Envia e-mails transacionais via SMTP usando JavaMailSender.
 * Se SMTP falhar por restrição de rede/porta, tenta fallback via API HTTP do Brevo.
 */
@Service
public class ExternalEmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(ExternalEmailSenderService.class);

    private final JavaMailSender mailSender;

    @Value("${mail.from:${spring.mail.username:}}")
    private String mailFrom;

    @Value("${mail.smtp-enabled:true}")
    private boolean smtpEnabled;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${brevo.from.name:Equilibra}")
    private String brevoFromName;

    @Value("${brevo.api-fallback-enabled:true}")
    private boolean brevoApiFallbackEnabled;

    @Value("${brevo.connect-timeout-ms:10000}")
    private int brevoConnectTimeoutMs;

    @Value("${brevo.read-timeout-ms:10000}")
    private int brevoReadTimeoutMs;

    public ExternalEmailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private RestClient buildBrevoClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(brevoConnectTimeoutMs);
        requestFactory.setReadTimeout(brevoReadTimeoutMs);

        return RestClient.builder()
                .baseUrl("https://api.brevo.com")
                .requestFactory(requestFactory)
                .build();
    }

    public void sendHtml(String destinatario, String assunto, String htmlContent) {
        if (destinatario == null || destinatario.isBlank()) {
            throw new MailSendException("Destinatário do e-mail não informado.");
        }
        if (assunto == null || assunto.isBlank()) {
            throw new MailSendException("Assunto do e-mail não informado.");
        }
        if (htmlContent == null || htmlContent.isBlank()) {
            throw new MailSendException("Conteúdo HTML do e-mail não informado.");
        }
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new MailSendException("MAIL_FROM ou MAIL_USERNAME não configurado.");
        }

        if (!smtpEnabled) {
            sendViaBrevoApi(destinatario, assunto, htmlContent);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();

            if (mimeMessage == null) {
                log.warn("JavaMailSender retornou MimeMessage nulo. Usando fallback local para composição da mensagem.");
                mimeMessage = new MimeMessage((Session) null);
            }

            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(htmlContent, true);

            if (htmlContent.contains("cid:equilibra-logo.png")) {
                ClassPathResource logo = new ClassPathResource("static/assets/logo-equilibra.png");
                if (logo.exists()) {
                    helper.addInline("equilibra-logo.png", logo);
                }
            }

            mailSender.send(mimeMessage);
            log.info("E-mail enviado via SMTP para {}", destinatario);
            return;
        } catch (Exception smtpEx) {
            if (!brevoApiFallbackEnabled) {
                throw new MailSendException("Falha ao enviar e-mail via SMTP.", smtpEx);
            }
            if (brevoApiKey == null || brevoApiKey.isBlank()) {
                throw new MailSendException("Falha ao enviar e-mail via SMTP e BREVO_API_KEY não configurada para fallback.", smtpEx);
            }

            log.warn("Falha no envio SMTP para {}. Tentando fallback via API do Brevo.", destinatario, smtpEx);

            try {
                sendViaBrevoApi(destinatario, assunto, htmlContent);
                return;
            } catch (Exception apiEx) {
                MailSendException exception = new MailSendException("Falha ao enviar e-mail via SMTP e via API do Brevo.", apiEx);
                exception.addSuppressed(smtpEx);
                throw exception;
            }
        }
    }

    private void sendViaBrevoApi(String destinatario, String assunto, String htmlContent) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new MailSendException("BREVO_API_KEY não configurada.");
        }

        Map<String, Object> payloadMap = new java.util.HashMap<>(Map.of(
                "sender", Map.of("name", brevoFromName, "email", mailFrom),
                "to", List.of(Map.of("email", destinatario)),
                "subject", assunto,
                "htmlContent", htmlContent
        ));

        // Tenta anexar a logo se estiver no HTML
        if (htmlContent.contains("cid:equilibra-logo.png")) {
            try {
                ClassPathResource logoRes = new ClassPathResource("static/assets/logo-equilibra.png");
                if (logoRes.exists()) {
                    byte[] imageBytes = logoRes.getInputStream().readAllBytes();
                    String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                    
                    payloadMap.put("attachment", List.of(Map.of(
                            "content", base64Image,
                            "name", "equilibra-logo.png"
                    )));
                }
            } catch (Exception e) {
                log.warn("Falha ao preparar logo para API do Brevo: {}", e.getMessage());
            }
        }

        try {
            buildBrevoClient().post()
                    .uri("/v3/smtp/email")
                    .header("api-key", brevoApiKey)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payloadMap)
                    .retrieve()
                    .toBodilessEntity();

            log.info("E-mail enviado via API Brevo para {}", destinatario);
        } catch (RestClientResponseException ex) {
            String resposta = ex.getResponseBodyAsString();
            if (ex.getStatusCode().value() == 401) {
                throw new MailSendException("Falha ao enviar e-mail via API do Brevo. Status=401 (não autorizado). Verifique se BREVO_API_KEY é uma API key válida da aba API Keys & MCP (xkeysib-...) e não uma chave SMTP.", ex);
            }
            throw new MailSendException("Falha ao enviar e-mail via API do Brevo. Status=" + ex.getStatusCode().value() + ", body=" + resposta, ex);
        }
    }
}
