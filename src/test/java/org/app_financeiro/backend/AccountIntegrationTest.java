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

    private String accessToken;
    private Long usuarioId;

    @BeforeEach
    void setUp() throws Exception {
        limparTodasAsTabelas();

        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        String email = "test@email.com";
        String senha = "SenhaSegura@123";

        this.accessToken = setupUsuarioVerificado("Test User", email, senha);
        this.usuarioId = usuarioRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void deveCriarEListarContasComSucesso() throws Exception {
        ContaRegistroRequestDTO contaReq = new ContaRegistroRequestDTO("Conta Corrente Teste", new BigDecimal("100.50"));

        mockMvc.perform(post("/api/contas")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contaReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Conta Corrente Teste"))
                .andExpect(jsonPath("$.saldo").value(100.50));

        mockMvc.perform(get("/api/contas")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("Conta Corrente Teste"));
    }

    @Test
    void deveCriarEListarCartoesComSucesso() throws Exception {
        CartaoRegistroRequestDTO cartaoReq = new CartaoRegistroRequestDTO(
                "NuBank Teste",
                new BigDecimal("5000.00"),
                5,
                10,
                BandeiraCartao.NUBANK,
                null
        );

        mockMvc.perform(post("/api/cartoes")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cartaoReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("NuBank Teste"))
                .andExpect(jsonPath("$.limite").value(5000.00));

        mockMvc.perform(get("/api/cartoes")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nome").value("NuBank Teste"));
    }
}
