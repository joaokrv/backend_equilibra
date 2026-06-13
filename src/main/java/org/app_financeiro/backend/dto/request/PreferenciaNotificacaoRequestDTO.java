package org.app_financeiro.backend.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Preferência de notificação por e-mail do usuário.
 * {@code notificacoesFaturaAtivo=false} interrompe todo envio de lembrete de fatura
 * (o cron filtra os destinatários por esta flag na própria query).
 */
public record PreferenciaNotificacaoRequestDTO(
        @NotNull(message = "A preferência de notificação é obrigatória")
        Boolean notificacoesFaturaAtivo
) {}
