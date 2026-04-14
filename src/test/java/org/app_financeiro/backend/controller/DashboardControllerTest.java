package org.app_financeiro.backend.controller;

import org.app_financeiro.backend.AbstractIntegrationTest;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private UsuarioEntity usuario;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();

        usuario = new UsuarioEntity();
        usuario.setNome("Dashboard Controller Test");
        usuario.setEmail("dashboard-controller-test@email.com");
        usuario.setSenha("SenhaSegura123");
        usuario = usuarioRepository.save(usuario);
    }

    @Test
    void deveRetornar200ComResumoPadrao() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value("1M"))
                .andExpect(jsonPath("$.totalReceitasAtual").isNumber())
                .andExpect(jsonPath("$.totalDespesasAtual").isNumber());
    }

    @Test
    void deveRetornar200ComPeriodo3M() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")
                        .param("periodo", "3M")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value("3M"));
    }

    @Test
    void deveRetornar422ParaPeriodoInvalido() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")
                        .param("periodo", "INVALIDO")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(user(usuario)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deveRetornar403SemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}