package org.app_financeiro.backend.service;

import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.repository.CartaoRepository;
import org.app_financeiro.backend.repository.FaturaRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FaturaSchedulerLockTest extends org.app_financeiro.backend.AbstractIntegrationTest {

    @Autowired
    private FaturaSchedulerService scheduler;

    @Autowired
    private FaturaRepository faturaRepository;

    @Autowired
    private CartaoRepository cartaoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() {
        faturaRepository.deleteAll();
        cartaoRepository.deleteAll();
        usuarioRepository.deleteAll();

        // create a user, card and two faturas past due date
        UsuarioEntity u = new UsuarioEntity();
        u.setNome("u");
        u = usuarioRepository.save(u);

        CartaoEntity c = new CartaoEntity();
        c.setNome("c");
        c.setLimite(new BigDecimal("1000"));
        c.setUsuario(u);
        c.setAtivo(true);
        c = cartaoRepository.save(c);

        for (int i = 0; i < 2; i++) {
            FaturaEntity f = new FaturaEntity();
            f.setCartao(c);
            f.setUsuario(u);
            f.setMes(LocalDate.now().getMonthValue());
            f.setAno(LocalDate.now().getYear());
            f.setValorTotal(new BigDecimal("100"));
            f.setValorPago(BigDecimal.ZERO);
            f.setStatus(StatusFatura.ABERTA);
            f.setDataFechamento(LocalDate.now().minusDays(2));
            f.setDataVencimento(LocalDate.now().minusDays(1));
            f.setAtivo(true);
            faturaRepository.save(f);
        }
    }

    @Test
    void deveExecutarApenasUmaVezQuandoChamadoConcorrentemente() throws InterruptedException {
        int threads = 2;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger updatedCount = new AtomicInteger();

        Runnable task = () -> {
            try {
                scheduler.atualizarFaturasAtrasadas();
                updatedCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        latch.await();

        // only one invocation should have successfully updated faturas
        assertThat(updatedCount.get()).isEqualTo(1);
    }
}
