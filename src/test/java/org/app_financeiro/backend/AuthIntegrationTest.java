package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    @MockBean
    private JavaMailSender mailSender;

    @BeforeEach
    void cleanUp() {
        codigoVerificacaoRepository.deleteAll();
        usuarioRepository.deleteAll();
        
        // Configura o mock para evitar NullPointerException ao criar MimeMessage
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
    }

    @Test
    void deveRegistrarVerificarELogarComSucesso() throws Exception {
        String email = "joao@email.com";
        String senha = "SenhaSegura123";

        // 1. REGISTRAR
        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Joao Victor", email, senha);

        mockMvc.perform(post("/api/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        // 2. BUSCAR CÓDIGO (Simulando recebimento de e-mail)
        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository.findAll()
                .stream()
                .filter(c -> c.getEmail().equals(email))
                .findFirst()
                .orElseThrow();

        // 3. VERIFICAR E-MAIL
        VerificarEmailRequestDTO verificarReq = new VerificarEmailRequestDTO(email, codigoEntity.getCodigo());

        mockMvc.perform(post("/api/auth/verificar-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verificarReq)))
                .andExpect(status().isOk());

        // 4. LOGIN
        UsuarioLoginRequestDTO loginReq = new UsuarioLoginRequestDTO(email, senha);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void naoDeveLogarSeEmailNaoVerificado() throws Exception {
        String email = "pendente@email.com";
        String senha = "SenhaSegura123";

        // Registrar sem verificar
        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Usuario Pendente", email, senha);
        mockMvc.perform(post("/api/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated());

        // Tentar Login
        UsuarioLoginRequestDTO loginReq = new UsuarioLoginRequestDTO(email, senha);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden()); // 403 - Conta desabilitada (e-mail não verificado)
    }
}
