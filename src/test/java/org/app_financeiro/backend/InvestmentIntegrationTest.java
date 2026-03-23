package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class InvestmentIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private InvestimentoRepository investimentoRepository;

    @Autowired
    private ContaRepository contaRepository;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    @MockBean
    private JavaMailSender mailSender;

    private String tokenA;
    private Long idUserA;

    @BeforeEach
    void setUp() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        investimentoRepository.deleteAllInBatch();
        contaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();

        tokenA = "Bearer " + setupUser("User A", "userA@email.com");
        idUserA = usuarioRepository.findByEmail("userA@email.com").get().getId();
    }

    private String setupUser(String nome, String email) throws Exception {
        UsuarioRegistroRequestDTO reg = new UsuarioRegistroRequestDTO(nome, email, "senha123");
        mockMvc.perform(post("/api/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        UsuarioEntity user = usuarioRepository.findByEmail(email).orElseThrow();
        user.setEmailVerificado(true);
        usuarioRepository.save(user);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UsuarioLoginRequestDTO(email, "senha123"))))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        return (String) response.get("accessToken");
    }

    private Long criarConta(String nome, BigDecimal saldo, Long usuarioId) {
        ContaEntity conta = new ContaEntity();
        conta.setNome(nome);
        conta.setSaldo(saldo);
        conta.setAtivo(true);
        conta.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        return contaRepository.saveAndFlush(conta).getId();
    }

    private Long salvarInvestimento(String descricao, BigDecimal inicial, BigDecimal meta, Long usuarioId) {
        InvestimentoEntity inv = new InvestimentoEntity();
        inv.setDescricao(descricao);
        inv.setValorInicial(inicial);
        inv.setValorAtual(inicial);
        inv.setMetaAtual(meta);
        inv.setAtivo(true);
        inv.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        return investimentoRepository.saveAndFlush(inv).getId();
    }

    // =============================================
    // TESTES DE SUCESSO
    // =============================================

    @Test
    void deveCriarInvestimentoComSucesso() throws Exception {
        InvestimentoRegistroRequestDTO dto = new InvestimentoRegistroRequestDTO("Carro Novo", new BigDecimal("500.00"), new BigDecimal("50000.00"));

        mockMvc.perform(post("/api/investimentos")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricao").value("Carro Novo"))
                .andExpect(jsonPath("$.valorAtual").value(500.0))
                .andExpect(jsonPath("$.metaAtual").value(50000.0));
    }

    @Test
    void deveListarInvestimentosDoUsuario() throws Exception {
        salvarInvestimento("Férias", new BigDecimal("100.00"), new BigDecimal("2000.00"), idUserA);
        salvarInvestimento("Aposentadoria", new BigDecimal("500.00"), new BigDecimal("1000000.00"), idUserA);

        mockMvc.perform(get("/api/investimentos")
                .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deveAdicionarDepositoComSucesso() throws Exception {
        Long idConta = criarConta("Nubank", new BigDecimal("1000.00"), idUserA);
        Long idInv = salvarInvestimento("Reserva", new BigDecimal("100.00"), new BigDecimal("5000.00"), idUserA);

        mockMvc.perform(post("/api/investimentos/" + idInv + "/depositar")
                .header("Authorization", tokenA)
                .param("valor", "400.00")
                .param("contaId", idConta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAtual").value(500.0));

        // Verificar saldo da conta decrementado
        ContaEntity conta = contaRepository.findById(idConta).orElseThrow();
        assertThat(conta.getSaldo()).isEqualByComparingTo("600.00");
    }

    @Test
    void deveResgatarInvestimentoComSucesso() throws Exception {
        Long idConta = criarConta("Inter", new BigDecimal("0.00"), idUserA);
        Long idInv = salvarInvestimento("Emergência", new BigDecimal("1000.00"), new BigDecimal("5000.00"), idUserA);

        mockMvc.perform(post("/api/investimentos/" + idInv + "/resgatar")
                .header("Authorization", tokenA)
                .param("valor", "300.00")
                .param("contaId", idConta.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorAtual").value(700.0));

        // Verificar saldo da conta incrementado
        ContaEntity conta = contaRepository.findById(idConta).orElseThrow();
        assertThat(conta.getSaldo()).isEqualByComparingTo("300.00");
    }

    @Test
    void deveAtualizarMetaComSucesso() throws Exception {
        Long idInv = salvarInvestimento("Casa", new BigDecimal("0.00"), new BigDecimal("200000.00"), idUserA);

        mockMvc.perform(put("/api/investimentos/" + idInv + "/meta")
                .header("Authorization", tokenA)
                .param("novaMeta", "250000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metaAtual").value(250000.0));
    }

    @Test
    void deveDeletarInvestimentoComSaldoZero() throws Exception {
        Long idInv = salvarInvestimento("Teste Delete", new BigDecimal("0.00"), new BigDecimal("100.00"), idUserA);

        mockMvc.perform(delete("/api/investimentos/" + idInv)
                .header("Authorization", tokenA))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        assertThat(investimentoRepository.findById(idInv)).isEmpty();
    }

    // =============================================
    // TESTES DE ERRO E VALIDACAO
    // =============================================

    @Test
    void naoDeveCriarInvestimentoComDescricaoEmBranco() throws Exception {
        InvestimentoRegistroRequestDTO dto = new InvestimentoRegistroRequestDTO("", new BigDecimal("100.00"), new BigDecimal("500.00"));

        mockMvc.perform(post("/api/investimentos")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void naoDeveDepositarSemSaldoNaConta() throws Exception {
        Long idConta = criarConta("Nubank", new BigDecimal("50.00"), idUserA);
        Long idInv = salvarInvestimento("Reserva", new BigDecimal("100.00"), new BigDecimal("5000.00"), idUserA);

        mockMvc.perform(post("/api/investimentos/" + idInv + "/depositar")
                .header("Authorization", tokenA)
                .param("valor", "100.00")
                .param("contaId", idConta.toString()))
                .andExpect(status().isUnprocessableEntity()) // Regra de negócio
                .andExpect(jsonPath("$.erro").value("Saldo insuficiente"))
                .andExpect(jsonPath("$.mensagem").value("Saldo insuficiente"));
    }

    @Test
    void naoDeveResgatarValorSuperiorAoSaldoDoInvestimento() throws Exception {
        Long idConta = criarConta("Inter", new BigDecimal("0.00"), idUserA);
        Long idInv = salvarInvestimento("Emergência", new BigDecimal("500.00"), new BigDecimal("5000.00"), idUserA);

        mockMvc.perform(post("/api/investimentos/" + idInv + "/resgatar")
                .header("Authorization", tokenA)
                .param("valor", "600.00")
                .param("contaId", idConta.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Erro de regra de negócio"))
                .andExpect(jsonPath("$.mensagem").value("Valor de resgate excede o saldo do investimento"));
    }

    @Test
    void naoDeveDeletarInvestimentoComSaldo() throws Exception {
        Long idInv = salvarInvestimento("Investimento Rico", new BigDecimal("100.00"), new BigDecimal("1000.00"), idUserA);

        mockMvc.perform(delete("/api/investimentos/" + idInv)
                .header("Authorization", tokenA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Erro de regra de negócio"));
    }

    // =============================================
    // TESTES DE SEGURANÇA E ISOLAMENTO
    // =============================================

    @Test
    void usuarioBNaoDeveVerInvestimentosDoUsuarioA() throws Exception {
        String tokenB = "Bearer " + setupUser("User B", "userB@email.com");
        salvarInvestimento("Secreto User A", new BigDecimal("100.00"), new BigDecimal("1000.00"), idUserA);

        mockMvc.perform(get("/api/investimentos")
                .header("Authorization", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void naoDeveDepositarEmInvestimentoDeOutroUsuario() throws Exception {
        String tokenB = "Bearer " + setupUser("User B", "userB@email.com");
        Long idUserB = usuarioRepository.findByEmail("userB@email.com").get().getId();
        
        Long idContaB = criarConta("Conta B", new BigDecimal("1000.00"), idUserB);
        Long idInvA = salvarInvestimento("Investimento A", new BigDecimal("100.00"), new BigDecimal("1000.00"), idUserA);

        mockMvc.perform(post("/api/investimentos/" + idInvA + "/depositar")
                .header("Authorization", tokenB)
                .param("valor", "100.00")
                .param("contaId", idContaB.toString()))
                .andExpect(status().isNotFound()); // Ou Forbidden, mas geralmente Not Found para esconder existência
    }
}
