package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.JobResultDTO;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.NotificacaoFaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.enums.StatusNotificacaoFatura;
import org.app_financeiro.backend.enums.TipoLembreteFatura;
import org.app_financeiro.backend.repository.FaturaRepository;
import org.app_financeiro.backend.repository.NotificacaoFaturaRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class FaturaLembreteService {

    private static final Logger log = LoggerFactory.getLogger(FaturaLembreteService.class);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR"));
    private static final List<StatusFatura> STATUS_ELEGIVEIS = List.of(StatusFatura.ABERTA, StatusFatura.FECHADA);

    private final FaturaRepository faturaRepository;
    private final NotificacaoFaturaRepository notificacaoRepository;
    private final TransacaoRepository transacaoRepository;
    private final ExternalEmailSenderService emailSenderService;

    public FaturaLembreteService(FaturaRepository faturaRepository,
                                  NotificacaoFaturaRepository notificacaoRepository,
                                  TransacaoRepository transacaoRepository,
                                  ExternalEmailSenderService emailSenderService) {
        this.faturaRepository = faturaRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.transacaoRepository = transacaoRepository;
        this.emailSenderService = emailSenderService;
    }

    public JobResultDTO executarJob() {
        LocalDate hoje = LocalDate.now();
        int processed = 0, sent = 0, skipped = 0, errors = 0;

        // D-3, D-1, D0
        TipoLembreteFatura[] tipos = {TipoLembreteFatura.D_3, TipoLembreteFatura.D_1, TipoLembreteFatura.D0};
        int[] offsets = {3, 1, 0};

        for (int i = 0; i < tipos.length; i++) {
            TipoLembreteFatura tipo = tipos[i];
            LocalDate dataAlvo = hoje.plusDays(offsets[i]);
            List<FaturaEntity> faturas = faturaRepository.findElegiveisParaLembrete(dataAlvo, STATUS_ELEGIVEIS);

            for (FaturaEntity fatura : faturas) {
                processed++;
                String key = buildKey(fatura.getId(), tipo, hoje);
                if (notificacaoRepository.existsByIdempotencyKey(key)) {
                    skipped++;
                    continue;
                }
                int resultado = processarNotificacao(fatura, tipo, hoje, key);
                if (resultado > 0) sent++;
                else if (resultado < 0) errors++;
            }
        }

        // ATRASADAS sem notificação prévia
        List<FaturaEntity> atrasadas = faturaRepository.findAtrasadasSemNotificacao(
                StatusFatura.ATRASADA, TipoLembreteFatura.ATRASO, StatusNotificacaoFatura.ENVIADO);

        for (FaturaEntity fatura : atrasadas) {
            processed++;
            String key = buildKey(fatura.getId(), TipoLembreteFatura.ATRASO, hoje);
            if (notificacaoRepository.existsByIdempotencyKey(key)) {
                skipped++;
                continue;
            }
            int resultado = processarNotificacao(fatura, TipoLembreteFatura.ATRASO, hoje, key);
            if (resultado > 0) sent++;
            else if (resultado < 0) errors++;
        }

        log.info("Job lembrete fatura concluído: processed={} sent={} skipped={} errors={}", processed, sent, skipped, errors);
        return new JobResultDTO(processed, sent, skipped, errors);
    }

    /**
     * Retorna 1 se enviado com sucesso, -1 se erro, 0 se não processado.
     * Cada notificação tem sua própria transação para não reverter envios já feitos.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processarNotificacao(FaturaEntity fatura, TipoLembreteFatura tipo, LocalDate hoje, String key) {
        NotificacaoFaturaEntity notificacao = new NotificacaoFaturaEntity();
        notificacao.setFaturaId(fatura.getId());
        notificacao.setUsuarioId(fatura.getUsuario().getId());
        notificacao.setTipo(tipo);
        notificacao.setScheduledAt(hoje);
        notificacao.setIdempotencyKey(key);
        notificacao.setStatus(StatusNotificacaoFatura.PENDENTE);
        notificacaoRepository.save(notificacao);

        int tentativas = 0;
        Exception ultimoErro = null;

        while (tentativas < 3) {
            try {
                String html = montarHtml(fatura, tipo);
                String assunto = montarAssunto(fatura, tipo);
                emailSenderService.sendHtml(fatura.getUsuario().getEmail(), assunto, html);

                notificacao.setStatus(StatusNotificacaoFatura.ENVIADO);
                notificacao.setSentAt(LocalDateTime.now());
                notificacao.setErro(null);
                notificacaoRepository.save(notificacao);
                log.info("Lembrete {} enviado para fatura {} (usuário {})", tipo, fatura.getId(), fatura.getUsuario().getId());
                return 1;
            } catch (Exception e) {
                ultimoErro = e;
                tentativas++;
                log.warn("Tentativa {}/3 falhou para fatura {} tipo {}: {}", tentativas, fatura.getId(), tipo, e.getMessage());
            }
        }

        notificacao.setStatus(StatusNotificacaoFatura.ERRO);
        notificacao.setErro(ultimoErro != null ? ultimoErro.getMessage() : "Erro desconhecido");
        notificacaoRepository.save(notificacao);
        log.error("Falha ao enviar lembrete {} para fatura {}", tipo, fatura.getId());
        return -1;
    }

    private String montarAssunto(FaturaEntity fatura, TipoLembreteFatura tipo) {
        String nomeCartao = fatura.getCartao().getNome();
        return switch (tipo) {
            case D_3   -> "Sua fatura do cartão " + nomeCartao + " vence em 3 dias";
            case D_1   -> "Sua fatura do cartão " + nomeCartao + " vence amanhã";
            case D0    -> "Sua fatura do cartão " + nomeCartao + " vence hoje";
            case ATRASO -> "Sua fatura do cartão " + nomeCartao + " está em atraso";
        };
    }

    private String montarHtml(FaturaEntity fatura, TipoLembreteFatura tipo) throws IOException {
        ClassPathResource resource = new ClassPathResource("templates/lembrete-fatura.html");
        String html = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        String tipoTexto = switch (tipo) {
            case D_3   -> "vence em 3 dias";
            case D_1   -> "vence amanhã";
            case D0    -> "vence hoje";
            case ATRASO -> "está em atraso";
        };

        String primeiroNome = primeiroNome(fatura.getUsuario().getNome());

        html = html.replace("{{NOME_USUARIO}}", primeiroNome)
                   .replace("{{NOME_CARTAO}}", fatura.getCartao().getNome())
                   .replace("{{TIPO_LEMBRETE}}", tipoTexto)
                   .replace("{{DATA_FECHAMENTO}}", fatura.getDataFechamento().format(DATA_BR))
                   .replace("{{DATA_VENCIMENTO}}", fatura.getDataVencimento().format(DATA_BR));

        // Valor total (opcional)
        if (fatura.getValorTotal() != null && fatura.getValorTotal().compareTo(BigDecimal.ZERO) > 0) {
            String blocoValor = """
                <tr>
                  <td style="padding:16px 20px;border-bottom:1px solid #1e1f2e;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                      <tr>
                        <td style="font-family:'Inter','Segoe UI',Tahoma,Arial,sans-serif;font-size:12px;font-weight:500;letter-spacing:0.5px;text-transform:uppercase;color:#5a667b;">
                          Valor Total
                        </td>
                        <td align="right" style="font-family:'Inter','Segoe UI',Tahoma,Arial,sans-serif;font-size:14px;font-weight:700;color:#68d391;">
                          R$ %s
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
                """.formatted(formatarValor(fatura.getValorTotal()));
            html = html.replace("{{BLOCO_VALOR_TOTAL}}", blocoValor);
        } else {
            html = html.replace("{{BLOCO_VALOR_TOTAL}}", "");
        }

        // Número de compras (opcional)
        long numCompras = transacaoRepository.countByFaturaId(fatura.getId());
        if (numCompras > 0) {
            String blocoCompras = """
                <tr>
                  <td style="padding:16px 20px;">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                      <tr>
                        <td style="font-family:'Inter','Segoe UI',Tahoma,Arial,sans-serif;font-size:12px;font-weight:500;letter-spacing:0.5px;text-transform:uppercase;color:#5a667b;">
                          Compras na Fatura
                        </td>
                        <td align="right" style="font-family:'Inter','Segoe UI',Tahoma,Arial,sans-serif;font-size:14px;font-weight:600;color:#e2e8f0;">
                          %d
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
                """.formatted(numCompras);
            html = html.replace("{{BLOCO_NUMERO_COMPRAS}}", blocoCompras);
        } else {
            html = html.replace("{{BLOCO_NUMERO_COMPRAS}}", "");
        }

        return html;
    }

    private String buildKey(Long faturaId, TipoLembreteFatura tipo, LocalDate hoje) {
        return faturaId + "-" + tipo.name() + "-" + hoje;
    }

    private String primeiroNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) return "usuário";
        return nomeCompleto.trim().split("\\s+")[0];
    }

    private String formatarValor(BigDecimal valor) {
        return String.format(new Locale("pt", "BR"), "%,.2f", valor);
    }
}
