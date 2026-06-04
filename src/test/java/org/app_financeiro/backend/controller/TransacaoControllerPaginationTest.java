package org.app_financeiro.backend.controller;

import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransacaoControllerPaginationTest extends org.app_financeiro.backend.AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    private UsuarioEntity usuario;

    @BeforeEach
    void setUp() {
        limparTodasAsTabelas();
        usuario = new UsuarioEntity();
        usuario.setNome("u");
        usuario.setEmail("u.pagination@email.com");
        usuario.setSenha("SenhaSegura@123");
        usuario = usuarioRepository.save(usuario);

        for (int i = 0; i < 3; i++) {
            TransacaoEntity t = new TransacaoEntity();
            t.setDescricao("t"+i);
            t.setUsuario(usuario);
            t.setTipo(org.app_financeiro.backend.enums.TipoTransacao.DESPESA);
            t.setStatus(org.app_financeiro.backend.enums.StatusTransacao.PENDENTE);
            t.setValor(BigDecimal.ZERO);
            t.setData(LocalDate.now());
            t.setIdempotencyKey("pagination-key-" + i + "-" + System.nanoTime());
            t.setAtivo(true);
            transacaoRepository.save(t);
        }
    }

    @Test
    void deveRetornarPaginaComParametros() throws Exception {
        mockMvc.perform(get("/api/transacoes?page=1&size=1")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void deveRetornar200ParaIntervaloValido() throws Exception {
        LocalDate inicio = LocalDate.now().minusDays(1);
        LocalDate fim = LocalDate.now().plusDays(1);

        mockMvc.perform(get("/api/transacoes/intervalo")
                        .param("dataInicio", inicio.toString())
                        .param("dataFim", fim.toString())
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void deveRetornar422QuandoDataFimAnteriorADataInicio() throws Exception {
        mockMvc.perform(get("/api/transacoes/intervalo")
                        .param("dataInicio", "2026-03-31")
                        .param("dataFim", "2026-01-01")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user(usuario)))
                .andExpect(status().isUnprocessableEntity());
    }
}
