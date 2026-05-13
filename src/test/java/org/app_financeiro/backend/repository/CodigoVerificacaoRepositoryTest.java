package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.BaseRepositoryTest;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CodigoVerificacaoRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Test
    @DisplayName("Deve salvar um Código de Verificação Onetime Password")
    void deveSalvarCodigoComSucesso() {
        CodigoVerificacaoEntity codigo = new CodigoVerificacaoEntity();
        codigo.setEmail("mario@email.com");
        codigo.setCodigo("ABC123");
        codigo.setDataExpiracao(LocalDateTime.now().plusMinutes(15));
        codigo.setUtilizado(false);

        CodigoVerificacaoEntity salvo = codigoVerificacaoRepository.save(codigo);

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getEmail()).isEqualTo("mario@email.com");
        assertThat(salvo.getCodigo()).isEqualTo("ABC123");
        assertThat(salvo.isUtilizado()).isFalse();
        assertThat(salvo.getDataCriacao()).isNotNull();
        assertThat(salvo.getDataExpiracao()).isNotNull();
    }
}
