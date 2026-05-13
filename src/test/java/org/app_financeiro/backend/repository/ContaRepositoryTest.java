package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.BaseRepositoryTest;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ContaRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UsuarioEntity usuarioSalvo;

    @BeforeEach
    void setUp() {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome("João Conta");
        usuario.setEmail("joao-conta@email.com");
        usuario.setSenha("123456");
        usuarioSalvo = usuarioRepository.save(usuario);
    }

    @Test
    @DisplayName("Deve salvar uma Conta corretamente no banco de dados e aplicar os defaults")
    void deveSalvarContaComSucesso() {
        ContaEntity novaConta = new ContaEntity();
        novaConta.setNome("Nubank");
        novaConta.setSaldo(BigDecimal.ZERO);
        novaConta.setUsuario(usuarioSalvo);

        ContaEntity contaSalva = contaRepository.save(novaConta);

        assertThat(contaSalva.getId()).isNotNull();
        assertThat(contaSalva.getNome()).isEqualTo("Nubank");
        assertThat(contaSalva.getSaldo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(contaSalva.isAtivo()).isTrue();
        assertThat(contaSalva.getDataCriacao()).isNotNull();
        assertThat(contaSalva.getDataAtualizacao()).isNotNull();
    }

    @Test
    @DisplayName("Deve inativar uma Conta (soft delete logic)")
    void deveInativarConta() {
        ContaEntity novaConta = new ContaEntity();
        novaConta.setNome("Inter");
        novaConta.setSaldo(new BigDecimal("150.00"));
        novaConta.setUsuario(usuarioSalvo);
        
        ContaEntity contaSalva = contaRepository.save(novaConta);
        assertThat(contaSalva.isAtivo()).isTrue();

        contaSalva.setAtivo(false);
        contaRepository.save(contaSalva);

        Optional<ContaEntity> busca = contaRepository.findById(contaSalva.getId());
        
        if (busca.isPresent()) {
            assertThat(busca.get().isAtivo()).isFalse();
        }
    }
}
