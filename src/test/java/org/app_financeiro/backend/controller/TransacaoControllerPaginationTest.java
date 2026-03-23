package org.app_financeiro.backend.controller;

import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
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
        transacaoRepository.deleteAll();
        usuario = new UsuarioEntity();
        usuario.setNome("u");
        usuario = usuarioRepository.save(usuario);

        for (int i = 0; i < 3; i++) {
            TransacaoEntity t = new TransacaoEntity();
            t.setDescricao("t"+i);
            t.setUsuario(usuario);
            t.setTipo(org.app_financeiro.backend.enums.TipoTransacao.DESPESA);
            t.setStatus(org.app_financeiro.backend.enums.StatusTransacao.PENDENTE);
            t.setValor(BigDecimal.ZERO);
            t.setData(LocalDate.now());
            t.setAtivo(true);
            transacaoRepository.save(t);
        }
    }

    @Test
    void deveRetornarPaginaComParametros() throws Exception {
        // login not needed since no security in integration? but security applies; we can bypass by @WithMockUser in controller tests but complicated
        // easier: disable security temporarily by annotation? Since other integration tests maybe already do so.

        mockMvc.perform(get("/api/transacoes?page=1&size=1")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user("user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3));
    }
}
