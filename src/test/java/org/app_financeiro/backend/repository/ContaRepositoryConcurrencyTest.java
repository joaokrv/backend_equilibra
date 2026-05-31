package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class ContaRepositoryConcurrencyTest {

    @Autowired
    private org.app_financeiro.backend.repository.ContaRepository contaRepository;

    @Autowired
    private org.app_financeiro.backend.repository.UsuarioRepository usuarioRepository;

    @Autowired
    private EntityManager em;

    @Test
    void quandoSalvarCopiaEstaleiraDeveLancarOptimisticLock() {
        UsuarioEntity user = new UsuarioEntity();
        user.setNome("usuario");
        user.setEmail("usuario.concorrencia@email.com");
        user.setSenha("SenhaSegura@123");
        user = usuarioRepository.save(user);

        ContaEntity conta = new ContaEntity();
        conta.setNome("Conta Teste");
        conta.setSaldo(new BigDecimal("100.00"));
        conta.setUsuario(user);
        conta.setAtivo(true);
        conta = contaRepository.saveAndFlush(conta);

        ContaEntity c1 = contaRepository.findById(conta.getId()).get();
        ContaEntity c2 = contaRepository.findById(conta.getId()).get();

        em.detach(c2);

        c1.setSaldo(c1.getSaldo().subtract(new BigDecimal("10")));
        contaRepository.saveAndFlush(c1);

        c2.setSaldo(c2.getSaldo().subtract(new BigDecimal("20")));
        assertThatThrownBy(() -> contaRepository.saveAndFlush(c2))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
