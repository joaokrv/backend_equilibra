package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class ContaRepositoryConcurrencyTest {

    @Autowired
    private org.app_financeiro.backend.repository.ContaRepository contaRepository;

    @Autowired
    private org.app_financeiro.backend.repository.UsuarioRepository usuarioRepository;

    @Test
    void quandoSalvarCopiaEstaleiraDeveLancarOptimisticLock() {
        // cria usuário e conta inicial
        UsuarioEntity user = new UsuarioEntity();
        user.setNome("usuario");
        user = usuarioRepository.save(user);

        ContaEntity conta = new ContaEntity();
        conta.setNome("Conta Teste");
        conta.setSaldo(new BigDecimal("100.00"));
        conta.setUsuario(user);
        conta.setAtivo(true);
        conta = contaRepository.save(conta);

        // carrega duas instâncias separadas
        ContaEntity c1 = contaRepository.findById(conta.getId()).get();
        ContaEntity c2 = contaRepository.findById(conta.getId()).get();

        // atualiza e salva a primeira
        c1.setSaldo(c1.getSaldo().subtract(new BigDecimal("10")));
        contaRepository.save(c1);

        // tenta salvar a segunda, que ainda carrega versão antiga
        c2.setSaldo(c2.getSaldo().subtract(new BigDecimal("20")));
        assertThatThrownBy(() -> contaRepository.save(c2))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
