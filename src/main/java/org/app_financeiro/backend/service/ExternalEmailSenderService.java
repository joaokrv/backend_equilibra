package org.app_financeiro.backend.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Envia e-mails transacionais via SMTP usando JavaMailSender.
 * Funciona com provedores como Brevo, Gmail e similares.
 */
@Service
public class ExternalEmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(ExternalEmailSenderService.class);

    private final JavaMailSender mailSender;

    @Value("${mail.from:${spring.mail.username:}}")
    private String mailFrom;

    public ExternalEmailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
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

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();

            if (mimeMessage == null) {
                log.warn("JavaMailSender retornou MimeMessage nulo. Usando fallback local para composição da mensagem.");
                mimeMessage = new MimeMessage((Session) null);
            }

            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("E-mail enviado via SMTP para {}", destinatario);
        } catch (MailSendException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new MailSendException("Falha ao enviar e-mail via SMTP.", ex);
        }
    }
}
