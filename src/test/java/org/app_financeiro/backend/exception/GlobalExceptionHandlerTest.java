package org.app_financeiro.backend.exception;

import org.app_financeiro.backend.dto.response.ErroResponseDTO;
import org.app_financeiro.backend.enums.ErrorCode;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private StaticMessageSource messageSource;

    @BeforeEach
    void setUp() {
        messageSource = new StaticMessageSource();
        messageSource.addMessage("error.saldo_insuficiente", Locale.ENGLISH, "Insufficient balance");
        messageSource.addMessage("error.saldo_insuficiente", new Locale("pt","BR"), "Saldo insuficiente");
        handler = new GlobalExceptionHandler(messageSource);
    }

    @Test
    void testSaldoInsuficienteDefaultLocale() {
        Locale.setDefault(new Locale("pt", "BR"));
        ResponseEntity<ErroResponseDTO> resp = handler.handleSaldoInsuficiente(new SaldoInsuficienteException("x"));
        ErroResponseDTO dto = resp.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.code()).isEqualTo(ErrorCode.SALDO_INSUFICIENTE.name());
        assertThat(dto.mensagem()).isEqualTo("Saldo insuficiente");
        assertThat(dto.status()).isEqualTo(422);
    }

    @Test
    void testSaldoInsuficienteEnglish() {
        Locale.setDefault(Locale.ENGLISH);
        ResponseEntity<ErroResponseDTO> resp = handler.handleSaldoInsuficiente(new SaldoInsuficienteException("x"));
        ErroResponseDTO dto = resp.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.mensagem()).isEqualTo("Insufficient balance");
    }
}
