package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.BaseRepositoryTest;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoInvestimento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvestimentoRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private InvestimentoRepository investimentoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UsuarioEntity usuarioSalvo;

    @BeforeEach
    void setUp() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome("Joaquim Silva");
        usuario.setEmail("joaquim.investimento@email.com");
        usuario.setSenha("123456");
        usuarioSalvo = usuarioRepository.save(usuario);
    }

    @Test
    @DisplayName("Deve salvar um Investimento corretamente e verificar campos")
    void deveSalvarInvestimentoComSucesso() {
        // Arrange
        InvestimentoEntity novoInvestimento = new InvestimentoEntity();
        novoInvestimento.setDescricao("Reserva de Emergência");
        novoInvestimento.setValorInicial(new BigDecimal("1000.00"));
        novoInvestimento.setValorAtual(new BigDecimal("1000.00"));
        novoInvestimento.setMetaAtual(new BigDecimal("10000.00"));
        novoInvestimento.setTipoInvestimento(TipoInvestimento.OUTRO);
        novoInvestimento.setUsuario(usuarioSalvo);

        // Act
        InvestimentoEntity investimentoSalvo = investimentoRepository.save(novoInvestimento);

        // Assert
        assertThat(investimentoSalvo.getId()).isNotNull();
        assertThat(investimentoSalvo.getDescricao()).isEqualTo("Reserva de Emergência");
        assertThat(investimentoSalvo.getValorInicial()).isEqualByComparingTo("1000.00");
        assertThat(investimentoSalvo.getValorAtual()).isEqualByComparingTo("1000.00");
        assertThat(investimentoSalvo.getMetaAtual()).isEqualByComparingTo("10000.00");
        assertThat(investimentoSalvo.isAtivo()).isTrue();
    }
}
