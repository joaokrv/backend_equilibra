package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.BaseRepositoryTest;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransacaoRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UsuarioEntity usuarioSalvo;
    private ContaEntity contaSalva;

    @BeforeEach
    void setUp() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome("Joao Silva");
        usuario.setEmail("joao.transacao@email.com");
        usuario.setSenha("123456");
        usuarioSalvo = usuarioRepository.save(usuario);

        ContaEntity conta = new ContaEntity();
        conta.setNome("Conta Corrente");
        conta.setSaldo(BigDecimal.ZERO);
        conta.setUsuario(usuarioSalvo);
        contaSalva = contaRepository.save(conta);
    }

    @Test
    @DisplayName("Deve salvar uma Transação de Despesa Pix associada a uma Conta Mapeada")
    void deveSalvarTransacaoDespesaPix() {
        TransacaoEntity transacao = new TransacaoEntity();
        transacao.setDescricao("Pizza");
        transacao.setValor(new BigDecimal("95.50"));
        transacao.setData(LocalDate.now());
        transacao.setTipo(TipoTransacao.DESPESA);
        transacao.setStatus(StatusTransacao.PAGO);
        transacao.setMetodoPagamento(MetodoPagamento.PIX);
        transacao.setConta(contaSalva);
        transacao.setUsuario(usuarioSalvo);
        transacao.setIdempotencyKey(UUID.randomUUID().toString());

        TransacaoEntity transacaoSalva = transacaoRepository.save(transacao);

        assertThat(transacaoSalva.getId()).isNotNull();
        assertThat(transacaoSalva.getDescricao()).isEqualTo("Pizza");
        assertThat(transacaoSalva.getValor()).isEqualByComparingTo(new BigDecimal("95.50"));
        assertThat(transacaoSalva.getTipo()).isEqualTo(TipoTransacao.DESPESA);
        assertThat(transacaoSalva.getStatus()).isEqualTo(StatusTransacao.PAGO);
        assertThat(transacaoSalva.getMetodoPagamento()).isEqualTo(MetodoPagamento.PIX);
        assertThat(transacaoSalva.isAtivo()).isTrue();
        assertThat(transacaoSalva.getUsuario().getId()).isEqualTo(usuarioSalvo.getId());
        assertThat(transacaoSalva.getConta().getId()).isEqualTo(contaSalva.getId());
    }
}
