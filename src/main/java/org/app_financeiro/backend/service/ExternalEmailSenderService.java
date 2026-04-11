package org.app_financeiro.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Envia e-mails transacionais via API HTTP do Resend.
 * Usa HTTPS (porta 443), evitando bloqueios comuns de SMTP em ambientes cloud.
 */
@Service
public class ExternalEmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(ExternalEmailSenderService.class);

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from:onboarding@resend.dev}")
    private String resendFrom;

    @Value("${resend.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    @Value("${resend.read-timeout-ms:10000}")
    private int readTimeoutMs;

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl("https://api.resend.com")
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
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new MailSendException("RESEND_API_KEY não configurada.");
        }
        if (resendFrom == null || resendFrom.isBlank()) {
            throw new MailSendException("RESEND_FROM não configurado.");
        }

        Map<String, Object> payload = Map.of(
                "from", resendFrom,
                "to", List.of(destinatario),
                "subject", assunto,
                "html", htmlContent
        );

        try {
            buildClient().post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resendApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("E-mail enviado via Resend para {}", destinatario);
        } catch (RestClientResponseException ex) {
            String resposta = ex.getResponseBodyAsString();
            throw new MailSendException("Falha ao enviar e-mail via Resend API. Status=" + ex.getStatusCode().value() + ", body=" + resposta, ex);
        } catch (Exception ex) {
            throw new MailSendException("Falha ao enviar e-mail via Resend API.", ex);
        }
    }
}
