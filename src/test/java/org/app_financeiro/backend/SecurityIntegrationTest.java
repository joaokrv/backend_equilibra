package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.repository.CartaoRepository;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de segurança (G12):
 * - G12.1: 401 sem token para todos os endpoints protegidos
 * - G12.2: IDOR — usuário B não acessa recursos de A
 * - G12.4: Token com assinatura adulterada → 401
 */
@DisplayName("Testes de Segurança — G12")
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private CartaoRepository cartaoRepository;

    @Autowired
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    private String tokenUsuarioA;
    private String tokenUsuarioB;
    private Long contaIdUsuarioA;

    @BeforeEach
    void setUp() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        tokenUsuarioA = criarUsuarioEObterToken("user-a@security.test", "UserA@123Secure");
        tokenUsuarioB = criarUsuarioEObterToken("user-b@security.test", "UserB@123Secure");

        // Usuário A cria uma conta
        ContaRegistroRequestDTO contaReq = new ContaRegistroRequestDTO("Conta de A", new BigDecimal("500.00"));
        MvcResult result = mockMvc.perform(post("/api/contas")
                        .header("Authorization", "Bearer " + tokenUsuarioA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contaReq)))
                .andExpect(status().isCreated())
                .andReturn();
        contaIdUsuarioA = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    @AfterEach
    void cleanUp() {
        cartaoRepository.deleteAllInBatch();
        contaRepository.deleteAllInBatch();
        codigoVerificacaoRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
    }

    // ─── G12.1 — 401 sem autenticação ────────────────────────────────────

    @Test
    @DisplayName("G12.1 — GET /api/contas sem token → 401")
    void deveRetornar401SemTokenContas() throws Exception {
        mockMvc.perform(get("/api/contas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.1 — GET /api/cartoes sem token → 401")
    void deveRetornar401SemTokenCartoes() throws Exception {
        mockMvc.perform(get("/api/cartoes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.1 — GET /api/categorias sem token → 401")
    void deveRetornar401SemTokenCategorias() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.1 — GET /api/investimentos sem token → 401")
    void deveRetornar401SemTokenInvestimentos() throws Exception {
        mockMvc.perform(get("/api/investimentos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.1 — GET /api/dashboard/resumo sem token → 401")
    void deveRetornar401SemTokenDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.1 — GET /api/usuarios/perfil/me sem token → 401")
    void deveRetornar401SemTokenPerfil() throws Exception {
        mockMvc.perform(get("/api/usuarios/perfil/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.1 — GET /api/faturas sem token → 401")
    void deveRetornar401SemTokenFaturas() throws Exception {
        mockMvc.perform(get("/api/faturas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.1 — GET /api/transacoes sem token → 401")
    void deveRetornar401SemTokenTransacoes() throws Exception {
        int ano = java.time.LocalDate.now().getYear();
        int mes = java.time.LocalDate.now().getMonthValue();
        mockMvc.perform(get("/api/transacoes").param("ano", String.valueOf(ano)).param("mes", String.valueOf(mes)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.1 — GET /api/recorrentes sem token → 401")
    void deveRetornar401SemTokenRecorrentes() throws Exception {
        mockMvc.perform(get("/api/recorrentes"))
                .andExpect(status().isUnauthorized());
    }

    // ─── G12.2 — IDOR: usuário B não acessa recurso de A ─────────────────

    @Test
    @DisplayName("G12.2 — IDOR: usuário B não acessa conta de A")
    void usuarioBNaoAcessaContaDeA() throws Exception {
        mockMvc.perform(get("/api/contas/{id}", contaIdUsuarioA)
                        .header("Authorization", "Bearer " + tokenUsuarioB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // 403 ou 404 são ambos aceitáveis para IDOR
                    assert status == 403 || status == 404
                            : "Esperado 403 ou 404, recebeu " + status;
                });
    }

    @Test
    @DisplayName("G12.2 — IDOR: usuário B não deleta conta de A")
    void usuarioBNaoDeletaContaDeA() throws Exception {
        mockMvc.perform(delete("/api/contas/{id}", contaIdUsuarioA)
                        .header("Authorization", "Bearer " + tokenUsuarioB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status == 403 || status == 404
                            : "Esperado 403 ou 404, recebeu " + status;
                });
    }

    // ─── G12.4 — Token com assinatura adulterada → 401 ───────────────────

    @Test
    @DisplayName("G12.4 — Token adulterado (assinatura falsa) → 401")
    void deveRetornar401ComTokenAdulterado() throws Exception {
        // Cortar a assinatura e substituir por texto falso
        String tokenAdulterado = tokenUsuarioA.substring(0, tokenUsuarioA.lastIndexOf('.')) + ".assinatura_falsa_aqui";

        mockMvc.perform(get("/api/contas")
                        .header("Authorization", "Bearer " + tokenAdulterado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.4 — Token com prefixo errado (sem 'Bearer') → 401")
    void deveRetornar401ComTokenSemPrefixoBearer() throws Exception {
        mockMvc.perform(get("/api/contas")
                        .header("Authorization", tokenUsuarioA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("G12.4 — Token completamente aleatório → 401")
    void deveRetornar401ComTokenAleatorio() throws Exception {
        mockMvc.perform(get("/api/contas")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlQGZha2UuY29tIn0.fake"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private String criarUsuarioEObterToken(String email, String senha) throws Exception {
        return setupUsuarioVerificado("Test User", email, senha);
    }
}
