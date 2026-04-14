package org.app_financeiro.backend.enums;

import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodoDashboardTest {

    @ParameterizedTest
    @CsvSource({
            "1M, UM_MES, 1",
            "3M, TRES_MESES, 3",
            "6M, SEIS_MESES, 6",
            "1A, UM_ANO, 12"
    })
    void deveConverterCodigoParaEnum(String codigo, String nomeEsperado, int mesesEsperados) {
        PeriodoDashboard periodo = PeriodoDashboard.fromCodigo(codigo);

        assertThat(periodo.name()).isEqualTo(nomeEsperado);
        assertThat(periodo.getQuantidadeMeses()).isEqualTo(mesesEsperados);
    }

    @ParameterizedTest
    @CsvSource({"1m", "3m", "6m", "1a"})
    void deveAceitarCodigoCaseInsensitive(String codigo) {
        PeriodoDashboard periodo = PeriodoDashboard.fromCodigo(codigo);

        assertThat(periodo).isNotNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void deveRetornarDefaultParaNuloOuVazio(String codigo) {
        PeriodoDashboard periodo = PeriodoDashboard.fromCodigo(codigo);

        assertThat(periodo).isEqualTo(PeriodoDashboard.UM_MES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2M", "5A", "abc", "12M"})
    void deveLancarExcecaoParaCodigoInvalido(String codigo) {
        assertThatThrownBy(() -> PeriodoDashboard.fromCodigo(codigo))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Periodo invalido");
    }

    @Test
    void deveRetornarCodigoCorreto() {
        assertThat(PeriodoDashboard.UM_MES.getCodigo()).isEqualTo("1M");
        assertThat(PeriodoDashboard.UM_ANO.getCodigo()).isEqualTo("1A");
    }
}