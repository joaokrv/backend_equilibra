package org.app_financeiro.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Manutenção de partições: cria a do ano seguinte e remove a de 3 anos atrás.
 * Verifica a aritmética de ano e o DDL idempotente, sem tocar o banco (JdbcTemplate mockado).
 * Os blocos try/catch devem manter a rotina resiliente — falha numa tabela não aborta a outra.
 */
@ExtendWith(MockitoExtension.class)
class PartitionMaintenanceServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PartitionMaintenanceService service;

    private List<String> capturarDdlExecutado() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).execute(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void devePrepararParticoesDoAnoSeguinteDeFormaIdempotente() {
        int anoSeguinte = LocalDate.now().getYear() + 1;

        service.prepararParticaoAnual();

        List<String> ddls = capturarDdlExecutado();
        assertThat(ddls).anySatisfy(sql -> assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS indicador_economico_" + anoSeguinte)
                .contains("FOR VALUES FROM ('" + anoSeguinte + "-01-01') TO ('" + (anoSeguinte + 1) + "-01-01')"));
        assertThat(ddls).anySatisfy(sql -> assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS patrimonio_historico_" + anoSeguinte));
    }

    @Test
    void deveRemoverParticoesDeTresAnosAtras() {
        int anoLimite = LocalDate.now().getYear() - 3;

        service.removerParticoesAntigas();

        List<String> ddls = capturarDdlExecutado();
        assertThat(ddls).contains(
                "DROP TABLE IF EXISTS indicador_economico_" + anoLimite + " CASCADE",
                "DROP TABLE IF EXISTS patrimonio_historico_" + anoLimite + " CASCADE");
    }

    @Test
    void falhaNaPrimeiraTabelaNaoAbortaASegunda() {
        // Primeira execução lança; a rotina deve capturar e seguir para a segunda tabela.
        doThrow(new RuntimeException("DDL falhou"))
                .doNothing()
                .when(jdbcTemplate).execute(anyString());

        assertThatCode(() -> service.prepararParticaoAnual()).doesNotThrowAnyException();

        verify(jdbcTemplate, times(2)).execute(anyString());
    }
}
