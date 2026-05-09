package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.app_financeiro.backend.dto.request.*;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.BandeiraCartao;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class TransactionIntegrationTest extends AbstractIntegrationTest {

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
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TransacaoRepository transacaoRepository;

    @Autowired
    private FaturaRepository faturaRepository;

    @Autowired
    private InvestimentoRepository investimentoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @MockBean
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    private String tokenA;
    private Long usuarioAId;
    private Long contaAId;
    private Long cartaoAId;
    private Long catDespesaAId;
    private Long catReceitaAId;

    private String tokenB;
    private Long usuarioBId;

    @BeforeEach
    void setUp() throws Exception {
        limparBanco();

        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        // Criar Usuário A e recursos
        tokenA = setupUser("User A", "userA@email.com");
        usuarioAId = usuarioRepository.findByEmail("userA@email.com").get().getId();
        
        contaAId = criarConta(tokenA, "Conta A", new BigDecimal("1000.00"));
        cartaoAId = criarCartao(tokenA, "Cartao A", new BigDecimal("5000.00"));
        catDespesaAId = criarCategoria(tokenA, "Alimentação", TipoTransacao.DESPESA);
        catReceitaAId = criarCategoria(tokenA, "Salário", TipoTransacao.RECEITA);

        // Criar Usuário B
        tokenB = setupUser("User B", "userB@email.com");
        usuarioBId = usuarioRepository.findByEmail("userB@email.com").get().getId();
    }

    private void limparBanco() {
        transacaoRepository.deleteAllInBatch();
        faturaRepository.deleteAllInBatch();
        investimentoRepository.deleteAllInBatch();
        cartaoRepository.deleteAllInBatch();
        contaRepository.deleteAllInBatch();
        categoriaRepository.deleteAllInBatch();
        codigoVerificacaoRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        entityManager.flush();
    }

    private String setupUser(String nome, String email) throws Exception {
        UsuarioRegistroRequestDTO reg = new UsuarioRegistroRequestDTO(nome, email, "SenhaSegura123");
        mockMvc.perform(post("/api/auth/registrar").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(reg)));
        
        UsuarioEntity user = usuarioRepository.findByEmail(email).get();
        user.setEmailVerificado(true);
        usuarioRepository.save(user);

        UsuarioLoginRequestDTO login = new UsuarioLoginRequestDTO(email, "SenhaSegura123");
        MvcResult res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(login))).andReturn();
        Map<String, String> tokens = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return tokens.get("accessToken");
    }

    private Long criarConta(String token, String nome, BigDecimal saldo) throws Exception {
        ContaRegistroRequestDTO req = new ContaRegistroRequestDTO(nome, saldo);
        MvcResult res = mockMvc.perform(post("/api/contas").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andReturn();
        Map<String, Object> map = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return ((Number) map.get("id")).longValue();
    }

    private Long criarCartao(String token, String nome, BigDecimal limite) throws Exception {
        CartaoRegistroRequestDTO req = new CartaoRegistroRequestDTO(nome, limite, 5, 10, BandeiraCartao.VISA, null);
        MvcResult res = mockMvc.perform(post("/api/cartoes").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andReturn();
        Map<String, Object> map = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return ((Number) map.get("id")).longValue();
    }

    private Long criarCategoria(String token, String nome, TipoTransacao tipo) throws Exception {
        CategoriaRegistroRequestDTO req = new CategoriaRegistroRequestDTO(nome, tipo);
        MvcResult res = mockMvc.perform(post("/api/categorias").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andReturn();
        Map<String, Object> map = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
        return ((Number) map.get("id")).longValue();
    }

    @Test
    void deveCriarDespesaEmContaEReduzirSaldo() throws Exception {
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Almoço", new BigDecimal("50.00"), LocalDate.now(), TipoTransacao.DESPESA, null, MetodoPagamento.PIX, contaAId, null, catDespesaAId, null, null, null, "key-int-1"
        );

        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        ContaEntity conta = contaRepository.findById(contaAId).get();
        assertThat(conta.getSaldo()).isEqualByComparingTo("950.00");
    }

    @Test
    void deveCriarDespesaNoCartaoEConsumirLimite() throws Exception {
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Televisão", new BigDecimal("1000.00"), LocalDate.now(), TipoTransacao.DESPESA, null, MetodoPagamento.CARTAO_CREDITO, null, cartaoAId, catDespesaAId, null, null, null, "key-int-2"
        );

        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // O limite do cartão é calculado dinamicamente via faturas. Buscamos pelo endpoint para validar o limite disponível.
        mockMvc.perform(get("/api/cartoes/" + cartaoAId).header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limiteDisponivel").value(4000.00));
    }

    @Test
    void naoDeveCriarDespesaSeSaldoInsuficiente() throws Exception {
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Carro Luxo", new BigDecimal("2000.00"), LocalDate.now(), TipoTransacao.DESPESA, null, MetodoPagamento.PIX, contaAId, null, catDespesaAId, null, null, null, "key-int-3"
        );

        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem").value("Saldo insuficiente"));
    }

    @Test
    void naoDevePermitirCategoriaIncompativel() throws Exception {
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Teste Erro", new BigDecimal("10.00"), LocalDate.now(), TipoTransacao.DESPESA, null, MetodoPagamento.PIX, contaAId, null, catReceitaAId, null, null, null, "key-int-4"
        );

        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void naoDevePermitirContaECartaoJuntos() throws Exception {
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Erro Duplo", new BigDecimal("10.00"), LocalDate.now(), TipoTransacao.DESPESA, null, MetodoPagamento.PIX, contaAId, cartaoAId, catDespesaAId, null, null, null, "key-int-5"
        );

        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deveValidarCamposObrigatorios() throws Exception {
        String jsonInvalido = """
            {
                "descricao": "",
                "valor": -10,
                "data": null,
                "tipo": null,
                "idempotencyKey": ""
            }
            """;

        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(jsonInvalido))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void naoDevePermitirUsuarioBAcessarContaDeUsuarioA() throws Exception {
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Invasao", new BigDecimal("10.00"), LocalDate.now(), TipoTransacao.DESPESA, null, MetodoPagamento.PIX, contaAId, null, null, null, null, null, "key-int-6"
        );

        // Usuario B tenta usar contaAId
        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenB).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveReverterSaldoAoDeletarTransacao() throws Exception {
        // ... (resto do código existente)
    }

    @Test
    void deveListarTransacoesDoUsuarioPorMesEAno() throws Exception {
        LocalDate hoje = LocalDate.now();
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Gasto Mes", new BigDecimal("10.00"), hoje, TipoTransacao.DESPESA, null, MetodoPagamento.PIX, contaAId, null, null, null, null, null, "key-int-7"
        );

        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transacoes")
                .header("Authorization", "Bearer " + tokenA)
                .param("ano", String.valueOf(hoje.getYear()))
                .param("mes", String.valueOf(hoje.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].descricao").value("Gasto Mes"));
    }

    @Test
    void deveListarDespesaNoMesDaFaturaQuandoCartaoFecharAntesDaCompra() throws Exception {
        LocalDate compraAposFechamento = LocalDate.of(2023, 10, 7);
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Compra Cartão", new BigDecimal("120.00"), compraAposFechamento, TipoTransacao.DESPESA, null,
            MetodoPagamento.CARTAO_CREDITO, null, cartaoAId, catDespesaAId, null, null, null, "key-int-10"
        );

        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/transacoes/mensal")
                .header("Authorization", "Bearer " + tokenA)
                .param("ano", "2023")
                .param("mes", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/transacoes/mensal")
                .header("Authorization", "Bearer " + tokenA)
                .param("ano", "2023")
                .param("mes", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].descricao").value("Compra Cartão"));
    }

    @Test
    void naoDevePermitirTotalParcelasInvalido() throws Exception {
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Parcela Erro", new BigDecimal("100.00"), LocalDate.now(), TipoTransacao.DESPESA, null, MetodoPagamento.CARTAO_CREDITO, null, cartaoAId, null, null, 1, 0, "key-int-8"
        );

        mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void retornarNotFoundAoDeletarIdInexistente() throws Exception {
        mockMvc.perform(delete("/api/transacoes/9999").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void naoDeveDeletarTransacaoDeOutroUsuario() throws Exception {
        // 1. User A cria transação
        TransacaoRegistroRequestDTO req = new TransacaoRegistroRequestDTO(
            "Segredo A", new BigDecimal("10.00"), LocalDate.now(), TipoTransacao.DESPESA, null, MetodoPagamento.PIX, contaAId, null, null, null, null, null, "key-int-9"
        );

        MvcResult res = mockMvc.perform(post("/api/transacoes").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        
        Long idA = ((Number) objectMapper.readValue(res.getResponse().getContentAsString(), Map.class).get("id")).longValue();

        // 2. User B tenta deletar idA
        mockMvc.perform(delete("/api/transacoes/" + idA).header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }
}
