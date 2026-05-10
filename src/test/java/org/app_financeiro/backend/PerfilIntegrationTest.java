package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.app_financeiro.backend.dto.request.UsuarioAtualizacaoRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.enums.MoedaEnum;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class PerfilIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() throws Exception {
        usuarioRepository.deleteAll();
        when(mailSender.createMimeMessage()).thenReturn(new org.springframework.mail.javamail.JavaMailSenderImpl().createMimeMessage());

        // Setup Usuário A
        tokenA = setupUsuarioVerificado("User A", "usera@email.com", "SenhaA123!");
        
        // Setup Usuário B
        tokenB = setupUsuarioVerificado("User B", "userb@email.com", "SenhaB123!");
    }

    @Test
    @DisplayName("Deve retornar os dados do perfil do usuário logado")
    void deveRetornarPerfilDoUsuarioLogado() throws Exception {
        mockMvc.perform(get("/api/usuarios/perfil/me")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("User A"))
                .andExpect(jsonPath("$.email").value("usera@email.com"));
    }

    @Test
    @DisplayName("Deve atualizar o perfil com dados válidos")
    void deveAtualizarPerfilComDadosValidos() throws Exception {
        UsuarioAtualizacaoRequestDTO req = new UsuarioAtualizacaoRequestDTO(
            "User A Modificado",
            "21999998888",
            MoedaEnum.USD
        );

        mockMvc.perform(put("/api/usuarios/perfil/me")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("User A Modificado"))
                .andExpect(jsonPath("$.celular").value("21999998888"))
                .andExpect(jsonPath("$.moeda").value("USD"));
    }

    @Test
    @DisplayName("Não deve permitir atualizar perfil com celular já em uso por outro usuário")
    void naoDevePermitirCelularDuplicado() throws Exception {
        // 1. Usuário B define seu celular
        UsuarioAtualizacaoRequestDTO reqB = new UsuarioAtualizacaoRequestDTO("User B", "21999998888", MoedaEnum.BRL);
        mockMvc.perform(put("/api/usuarios/perfil/me")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isOk());

        // 2. Usuário A tenta definir o mesmo celular
        UsuarioAtualizacaoRequestDTO reqA = new UsuarioAtualizacaoRequestDTO("User A", "21999998888", MoedaEnum.BRL);
        mockMvc.perform(put("/api/usuarios/perfil/me")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Não deve permitir celular com formato inválido (contendo letras)")
    void naoDevePermitirCelularInvalido() throws Exception {
        UsuarioAtualizacaoRequestDTO req = new UsuarioAtualizacaoRequestDTO("User A", "2199999AAAA", MoedaEnum.BRL);

        mockMvc.perform(put("/api/usuarios/perfil/me")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }
}
