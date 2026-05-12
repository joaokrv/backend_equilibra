package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.entity.UsuarioPendenteEntity;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
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

/**
 * Testes de integração para o fluxo de autenticação com pré-registro e OTP obrigatório.
 * Alinhado com a RFC-Registro-OTP-Obrigatorio-2026-05-10.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Autowired
    private UsuarioPendenteRepository usuarioPendenteRepository;

    @BeforeEach
    void cleanUp() {
        codigoVerificacaoRepository.deleteAll();
        usuarioPendenteRepository.deleteAll();
        usuarioRepository.deleteAll();

        // Configura o mock para evitar NullPointerException ao criar MimeMessage
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
    }

    /**
     * RFC 7.1 + 7.2: Fluxo completo — pré-registrar → verificar OTP → login com sucesso.
     */
    @Test
    void devePreRegistrarVerificarELogarComSucesso() throws Exception {
        String email = "joao@email.com";
        String senha = "SenhaSegura123!";

        // 1. PRÉ-REGISTRAR — retorna registroId e status ATIVO (RFC 7.1)
        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Joao Victor", email, senha);

        MvcResult preRegistroResult = mockMvc.perform(post("/api/auth/pre-registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registroId").exists())
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.tentativasRestantes").value(5))
                .andReturn();

        String registroId = objectMapper.readTree(preRegistroResult.getResponse().getContentAsString())
                .get("registroId").asText();

        // 2. BUSCAR CÓDIGO (Simulando recebimento de e-mail)
        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository.findAll()
                .stream()
                .filter(c -> c.getEmail().equals(email))
                .findFirst()
                .orElseThrow();

        // 3. VERIFICAR E-MAIL via registroId (RFC 7.2)
        VerificarEmailRequestDTO verificarReq = new VerificarEmailRequestDTO(email, codigoEntity.getCodigo(), registroId);

        mockMvc.perform(post("/api/auth/verificar-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verificarReq)))
                .andExpect(status().isOk());

        // 4. Verifica que o usuário definitivo foi criado com emailVerificado=true (RFC 7.2)
        UsuarioEntity usuario = usuarioRepository.findByEmail(email).orElseThrow();
        assertThat(usuario.isEmailVerificado()).isTrue();

        // 5. Verifica que o pré-registro foi removido (RFC 7.2)
        assertThat(usuarioPendenteRepository.findByEmail(email)).isEmpty();

        // 6. LOGIN — accessToken no body, refreshToken no cookie HttpOnly (G5)
        UsuarioLoginRequestDTO loginReq = new UsuarioLoginRequestDTO(email, senha);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").value((Object) null)) // null no body — RT somente via cookie (G5)
                .andExpect(jsonPath("$.usuario").exists())
                .andExpect(jsonPath("$.usuario.email").value(email))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true));
    }

    /**
     * RFC 7.5: Tentativa de login para e-mail não verificado deve retornar 403 com otpStatus.
     */
    @Test
    void naoDeveLogarSeEmailNaoVerificado() throws Exception {
        String email = "pendente@email.com";
        String senha = "SenhaSegura123!";

        // Pré-registrar sem verificar OTP
        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Usuario Pendente", email, senha);
        mockMvc.perform(post("/api/auth/pre-registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isOk());

        // Simula que o usuário foi "promovido" parcialmente (cenário legado ou manual)
        // Criando um usuário real com emailVerificado=false para testar a rota de login
        UsuarioPendenteEntity pendente = usuarioPendenteRepository.findByEmail(email).orElseThrow();
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setNome(pendente.getNome());
        usuario.setEmail(pendente.getEmail());
        usuario.setSenha(pendente.getSenhaHash());
        usuario.setEmailVerificado(false);
        usuarioRepository.save(usuario);

        // Tentar Login — deve retornar 403 com otpStatus (RFC 7.5)
        UsuarioLoginRequestDTO loginReq = new UsuarioLoginRequestDTO(email, senha);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.otpStatus").exists())
                .andExpect(jsonPath("$.otpStatus.registroId").exists());
    }

    /**
     * Fase 1 do RFC do OTP: uma tentativa inválida não deve ser perdida por rollback-only.
     * O backend precisa persistir a falha e responder com erro funcional previsível.
     */
    @Test
    void devePersistirTentativaFalhaQuandoOtpForInvalido() throws Exception {
        String email = "otp-invalido@email.com";
        String senha = "SenhaSegura123!";

        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Usuario OTP Invalido", email, senha);
        MvcResult preRegistroResult = mockMvc.perform(post("/api/auth/pre-registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isOk())
                .andReturn();

        String registroId = objectMapper.readTree(preRegistroResult.getResponse().getContentAsString())
                .get("registroId").asText();

        VerificarEmailRequestDTO verificarReq = new VerificarEmailRequestDTO(email, "000000", registroId);

        mockMvc.perform(post("/api/auth/verificar-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verificarReq)))
                .andExpect(status().isUnprocessableEntity());

        UsuarioPendenteEntity pendente = usuarioPendenteRepository.findByEmail(email).orElseThrow();
        assertThat(pendente.getTentativasFalhas()).isEqualTo(1);
        assertThat(pendente.getBloqueadoAte()).isNull();
        assertThat(pendente.getUltimaTentativaEm()).isNotNull();

        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository.findAll()
                .stream()
                .filter(c -> c.getEmail().equals(email))
                .findFirst()
                .orElseThrow();

        assertThat(codigoEntity.getTentativasFalhas()).isEqualTo(1);
        assertThat(codigoEntity.isUtilizado()).isFalse();
    }

    /**
     * RFC 10.1: Pré-registro com e-mail já existente retorna sucesso genérico (anti-enumeração).
     */
    @Test
    void preRegistroComEmailExistenteRetornaSucessoGenerico() throws Exception {
        // Criar usuário real já verificado
        UsuarioEntity existente = new UsuarioEntity();
        existente.setNome("Já Existe");
        existente.setEmail("existe@email.com");
        existente.setSenha("$argon2id$hash");
        existente.setEmailVerificado(true);
        usuarioRepository.save(existente);

        // Tentar pré-registro com mesmo e-mail
        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Outro Nome", "existe@email.com", "SenhaSegura123!");
        mockMvc.perform(post("/api/auth/pre-registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isOk()) // Resposta genérica — sem revelar que existe
                .andExpect(jsonPath("$.registroId").exists())
                .andExpect(jsonPath("$.status").value("ATIVO"));

        // NÃO deve ter criado pré-registro
        assertThat(usuarioPendenteRepository.findByEmail("existe@email.com")).isEmpty();
    }

    /**
     * RFC G5-A1/A2/A3: Novo login invalida RT anterior. RT é rotacionado a cada uso.
     */
    @Test
    void novoLoginDeveInvalidarRefreshTokenAnterior() throws Exception {
        String email = "sessao@email.com";
        String senha = "SenhaSegura123!";

        // Setup: criar usuário verificado diretamente no banco
        registrarEVerificarUsuario(email, senha, "Usuario Sessao");

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
                        .header("Origin", "http://localhost:3000")
                        .cookie(new Cookie("refreshToken", rtAntigo)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * RFC G5-A2/A3: Refresh token rota e reuso detectado invalida todas as sessões.
     */
    @Test
    void refreshTokenRotacionaACadaUso() throws Exception {
        String email = "rotation@email.com";
        String senha = "SenhaSegura123!";

        // Setup: criar usuário verificado diretamente no banco
        registrarEVerificarUsuario(email, senha, "Rotation User");

        UsuarioLoginRequestDTO loginReq = new UsuarioLoginRequestDTO(email, senha);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String rtInicial = login.getResponse().getCookie("refreshToken").getValue();

        // Primeiro refresh — usa RT inicial, recebe novo RT
        MvcResult primeiroRefresh = mockMvc.perform(post("/api/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .cookie(new Cookie("refreshToken", rtInicial)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.usuario").exists())
                .andReturn();

        String rtRotacionado = primeiroRefresh.getResponse().getCookie("refreshToken").getValue();
        assertThat(rtRotacionado).isNotEqualTo(rtInicial);

        // Reutilizar RT inicial (reuse attack) → 401 + sessão invalidada
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .cookie(new Cookie("refreshToken", rtInicial)))
                .andExpect(status().isUnauthorized());

        // RT rotacionado também deve ser rejeitado (chaveSessao foi zerada)
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Origin", "http://localhost:3000")
                        .cookie(new Cookie("refreshToken", rtRotacionado)))
                .andExpect(status().isUnauthorized());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    /**
     * Helper: faz o fluxo completo de pré-registro + verificação via OTP,
     * criando um usuário verificado no banco para os testes de login/refresh.
     */
    private void registrarEVerificarUsuario(String email, String senha, String nome) throws Exception {
        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO(nome, email, senha);
        MvcResult result = mockMvc.perform(post("/api/auth/pre-registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isOk())
                .andReturn();

        String registroId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("registroId").asText();

        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository.findAll()
                .stream()
                .filter(c -> c.getEmail().equals(email))
                .findFirst()
                .orElseThrow();

        VerificarEmailRequestDTO verificarReq = new VerificarEmailRequestDTO(email, codigoEntity.getCodigo(), registroId);
        mockMvc.perform(post("/api/auth/verificar-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verificarReq)))
                .andExpect(status().isOk());
    }
}
