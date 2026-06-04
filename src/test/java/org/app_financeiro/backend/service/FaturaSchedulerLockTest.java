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
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        limparTodasAsTabelas();

        UsuarioEntity u = new UsuarioEntity();
        u.setNome("u");
        u.setEmail("u.scheduler@email.com");
        u.setSenha("SenhaSegura@123");
        u = usuarioRepository.save(u);

        CartaoEntity c = new CartaoEntity();
        c.setNome("c");
        c.setLimite(new BigDecimal("1000"));
        c.setUsuario(u);
        c.setAtivo(true);
        c.setDiaFechamento(20);
        c.setDiaVencimento(10);
        c = cartaoRepository.save(c);

        for (int i = 0; i < 2; i++) {
            FaturaEntity f = new FaturaEntity();
            f.setCartao(c);
            f.setUsuario(u);
            // Meses distintos para não violar o índice único (cartao_id, mes, ano).
            LocalDate ref = LocalDate.now().minusMonths(i);
            f.setMes(ref.getMonthValue());
            f.setAno(ref.getYear());
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
    void deveAplicarLockEProcessarComSegurancaSobConcorrencia() throws InterruptedException {
        int threads = 2;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger completedCount = new AtomicInteger();

        Runnable task = () -> {
            try {
                scheduler.atualizarFaturasAtrasadas();
                completedCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        latch.await();

        // ShedLock pula a execução concorrente silenciosamente (não lança); ambas as chamadas retornam.
        assertThat(completedCount.get()).isEqualTo(threads);

        // O lock da tarefa foi registrado pelo ShedLock (mecanismo de exclusão ativo).
        Integer locks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shedlock WHERE name = 'atualizarFaturasAtrasadas'", Integer.class);
        assertThat(locks).isEqualTo(1);

        // Resultado de negócio correto e idempotente sob concorrência: faturas vencidas viram ATRASADA.
        assertThat(faturaRepository.findAll())
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.getStatus()).isEqualTo(StatusFatura.ATRASADA));
    }
}
