package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.BaseRepositoryTest;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CartaoRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private CartaoRepository cartaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UsuarioEntity usuarioSalvo;

    @BeforeEach
    void setUp() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome("Maria Cartão");
        usuario.setEmail("maria.cartao@email.com");
        usuario.setSenha("123456");
        usuarioSalvo = usuarioRepository.save(usuario);
    }

    @Test
    @DisplayName("Deve salvar um Cartão de Crédito corretamente no banco de dados com suas restrições")
    void deveSalvarCartaoComSucesso() {
        // Arrange
        CartaoEntity novoCartao = new CartaoEntity();
        novoCartao.setNome("C6 Bank");
        novoCartao.setLimite(new BigDecimal("2500.00"));
        novoCartao.setDiaVencimento(10);
        novoCartao.setDiaFechamento(3);
        novoCartao.setUsuario(usuarioSalvo);

        // Act
        CartaoEntity cartaoSalvo = cartaoRepository.save(novoCartao);

        // Assert
        assertThat(cartaoSalvo.getId()).isNotNull();
        assertThat(cartaoSalvo.getNome()).isEqualTo("C6 Bank");
        assertThat(cartaoSalvo.getLimite()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(cartaoSalvo.getDiaVencimento()).isEqualTo(10);
        assertThat(cartaoSalvo.getDiaFechamento()).isEqualTo(3);
        assertThat(cartaoSalvo.isAtivo()).isTrue();
        assertThat(cartaoSalvo.getDataCriacao()).isNotNull();
        assertThat(cartaoSalvo.getDataAtualizacao()).isNotNull();
    }
}
