package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.app_financeiro.backend.dto.request.CartaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.enums.BandeiraCartao;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.repository.CartaoRepository;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountIntegrationTest extends AbstractIntegrationTest {

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

    @MockBean
    private JavaMailSender mailSender;

    private String accessToken;
    private Long usuarioId;

    @BeforeEach
    void setUp() throws Exception {
        cartaoRepository.deleteAll();
        contaRepository.deleteAll();
        codigoVerificacaoRepository.deleteAll();
        usuarioRepository.deleteAll();

        // Configura o mock do e-mail
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        // 1. Registrar e autenticar um usuário para os testes
        String email = "test@email.com";
        String senha = "SenhaSegura123";

        // Registrar
        UsuarioRegistroRequestDTO registroReq = new UsuarioRegistroRequestDTO("Test User", email, senha);
        mockMvc.perform(post("/api/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registroReq)))
                .andExpect(status().isCreated());

        // Verificar e-mail manualmente no banco para simplificar o teste de fluxo
        UsuarioEntity usuario = usuarioRepository.findByEmail(email).orElseThrow();
        usuario.setEmailVerificado(true);
        usuarioRepository.save(usuario);
        this.usuarioId = usuario.getId();

        // Login
        UsuarioLoginRequestDTO loginReq = new UsuarioLoginRequestDTO(email, senha);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        Map<String, String> tokens = objectMapper.readValue(responseBody, Map.class);
        this.accessToken = tokens.get("accessToken");
    }

    @Test
    void deveCriarEListarContasComSucesso() throws Exception {
        ContaRegistroRequestDTO contaReq = new ContaRegistroRequestDTO("Conta Corrente Teste", new BigDecimal("100.50"));

        // Criar Conta
        mockMvc.perform(post("/api/contas")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contaReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Conta Corrente Teste"))
                .andExpect(jsonPath("$.saldo").value(100.50));

        // Listar Contas
        mockMvc.perform(get("/api/contas")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Conta Corrente Teste"));
    }

    @Test
    void deveCriarEListarCartoesComSucesso() throws Exception {
        // Para criar um cartão, geralmente não precisa de conta dependendo da regra, 
        // mas vamos ver se o DTO exige algo.
        CartaoRegistroRequestDTO cartaoReq = new CartaoRegistroRequestDTO(
                "NuBank Teste", 
                new BigDecimal("5000.00"), 
                5, 
                10,
                BandeiraCartao.NUBANK
        );

        // Criar Cartão
        mockMvc.perform(post("/api/cartoes")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cartaoReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("NuBank Teste"))
                .andExpect(jsonPath("$.limite").value(5000.00));

        // Listar Cartões
        mockMvc.perform(get("/api/cartoes")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("NuBank Teste"));
    }
}
