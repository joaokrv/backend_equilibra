package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.BaseRepositoryTest;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FaturaRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private FaturaRepository faturaRepository;

    @Autowired
    private CartaoRepository cartaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private CartaoEntity cartaoSalvo;

    @BeforeEach
    void setUp() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome("Joana Silva");
        usuario.setEmail("joana.fatura@email.com");
        usuario.setSenha("123456");
        UsuarioEntity usuarioSalvo = usuarioRepository.save(usuario);

        CartaoEntity cartao = new CartaoEntity();
        cartao.setNome("Nubank");
        cartao.setLimite(new BigDecimal("5000.00"));
        cartao.setDiaVencimento(10);
        cartao.setDiaFechamento(3);
        cartao.setUsuario(usuarioSalvo);
        cartaoSalvo = cartaoRepository.save(cartao);
    }

    @Test
    @DisplayName("Deve salvar uma Fatura de Cartão de Crédito corretamente no banco")
    void deveSalvarFaturaComSucesso() {
        // Arrange
        FaturaEntity fatura = new FaturaEntity();
        fatura.setCartao(cartaoSalvo);
        fatura.setUsuario(cartaoSalvo.getUsuario());
        fatura.setMes(10);
        fatura.setAno(2023);
        fatura.setDataVencimento(LocalDate.of(2023, 10, 10));
        fatura.setDataFechamento(LocalDate.of(2023, 10, 3));
        fatura.setValorTotal(new BigDecimal("150.00"));
        fatura.setStatus(StatusFatura.ABERTA);

        // Act
        FaturaEntity faturaSalva = faturaRepository.save(fatura);

        // Assert
        assertThat(faturaSalva.getId()).isNotNull();
        assertThat(faturaSalva.getStatus()).isEqualTo(StatusFatura.ABERTA);
        assertThat(faturaSalva.getValorTotal()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(faturaSalva.isAtivo()).isTrue();
    }
}
