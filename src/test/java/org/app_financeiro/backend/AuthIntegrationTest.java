package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
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

        // 1. REGISTRAR — resposta é mensagem genérica (G3 anti-enumeração)
        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Joao Victor", email, senha);

        mockMvc.perform(post("/api/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated());

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

        // 4. LOGIN — accessToken no body, refreshToken no cookie HttpOnly (G5)
        UsuarioLoginRequestDTO loginReq = new UsuarioLoginRequestDTO(email, senha);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").value(null)) // null no body — RT somente via cookie (G5)
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));
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

    @Test
    void novoLoginDeveInvalidarRefreshTokenAnterior() throws Exception {
        String email = "sessao@email.com";
        String senha = "SenhaSegura123";

        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Usuario Sessao", email, senha);
        mockMvc.perform(post("/api/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated());

        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository.findAll()
                .stream()
                .filter(c -> c.getEmail().equals(email))
                .findFirst()
                .orElseThrow();

        // Verificar e-mail diretamente no banco para simplificar
        UsuarioEntity usuario = usuarioRepository.findByEmail(email).orElseThrow();
        usuario.setEmailVerificado(true);
        usuarioRepository.save(usuario);

        UsuarioLoginRequestDTO loginReq = new UsuarioLoginRequestDTO(email, senha);

        // Primeiro login — captura cookie com RT antigo (G5)
        MvcResult primeiroLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String rtAntigo = primeiroLogin.getResponse().getCookie("refreshToken") != null
                ? primeiroLogin.getResponse().getCookie("refreshToken").getValue()
                : null;
        assertThat(rtAntigo).isNotBlank();

        // Segundo login — gera nova chaveSessao, invalida RT antigo
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk());

        // RT antigo enviado via cookie deve ser rejeitado (chaveSessao não bate)
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", rtAntigo)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenRotacionaACadaUso() throws Exception {
        String email = "rotation@email.com";
        String senha = "SenhaSegura123";

        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Rotation User", email, senha);
        mockMvc.perform(post("/api/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated());

        // Verificar e-mail no banco diretamente
        UsuarioEntity usuario = usuarioRepository.findByEmail(email).orElseThrow();
        usuario.setEmailVerificado(true);
        usuarioRepository.save(usuario);

        UsuarioLoginRequestDTO loginReq = new UsuarioLoginRequestDTO(email, senha);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String rtInicial = login.getResponse().getCookie("refreshToken").getValue();

        // Primeiro refresh — usa RT inicial, recebe novo RT
        MvcResult primeiroRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", rtInicial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String rtRotacionado = primeiroRefresh.getResponse().getCookie("refreshToken").getValue();
        assertThat(rtRotacionado).isNotEqualTo(rtInicial);

        // Reutilizar RT inicial (reuse attack) → 401 + sessão invalidada
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", rtInicial)))
                .andExpect(status().isUnauthorized());

        // RT rotacionado também deve ser rejeitado (chaveSessao foi zerada)
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", rtRotacionado)))
                .andExpect(status().isUnauthorized());
    }
}
