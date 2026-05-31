package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.enums.TipoInvestimento;
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

    private String tokenA;
    private Long idUserA;

    @BeforeEach
    void setUp() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        investimentoRepository.deleteAllInBatch();
        contaRepository.deleteAllInBatch();
        codigoVerificacaoRepository.deleteAllInBatch();
        usuarioPendenteRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();

        tokenA = "Bearer " + setupUsuarioVerificado("User A", "usera@email.com", "Senha@123");
        idUserA = usuarioRepository.findByEmail("usera@email.com").get().getId();
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
        inv.setTipoInvestimento(TipoInvestimento.OUTRO);
        inv.setAtivo(true);
        inv.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        return investimentoRepository.saveAndFlush(inv).getId();
    }


    @Test
    void deveCriarInvestimentoComSucesso() throws Exception {
        Long idConta = criarConta("Conta Origem", new BigDecimal("10000.00"), idUserA);
        InvestimentoRegistroRequestDTO dto = new InvestimentoRegistroRequestDTO("Carro Novo", new BigDecimal("500.00"), new BigDecimal("50000.00"), idConta, null, TipoInvestimento.CDB, null);

        mockMvc.perform(post("/api/investimentos")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricao").value("Carro Novo"))
                .andExpect(jsonPath("$.valorAtual").value(500.0))
                .andExpect(jsonPath("$.metaAtual").value(50000.0));

        ContaEntity conta = contaRepository.findById(idConta).orElseThrow();
        assertThat(conta.getSaldo()).isEqualByComparingTo("9500.00");
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


    @Test
    void naoDeveCriarInvestimentoComDescricaoEmBranco() throws Exception {
        Long idConta = criarConta("Conta Teste", new BigDecimal("1000.00"), idUserA);
        InvestimentoRegistroRequestDTO dto = new InvestimentoRegistroRequestDTO("", new BigDecimal("100.00"), new BigDecimal("500.00"), idConta, null, TipoInvestimento.CDB, null);

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
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Saldo insuficiente"))
                .andExpect(jsonPath("$.code").value("SALDO_INSUFICIENTE"));
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
                .andExpect(jsonPath("$.code").value("REGRA_DE_NEGOCIO"));
    }

    @Test
    void naoDeveDeletarInvestimentoComSaldo() throws Exception {
        Long idInv = salvarInvestimento("Investimento Rico", new BigDecimal("100.00"), new BigDecimal("1000.00"), idUserA);

        mockMvc.perform(delete("/api/investimentos/" + idInv)
                .header("Authorization", tokenA))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Erro de regra de negócio"));
    }


    @Test
    void usuarioBNaoDeveVerInvestimentosDoUsuarioA() throws Exception {
        String tokenB = "Bearer " + setupUsuarioVerificado("User B", "userb@email.com", "Senha@123");
        salvarInvestimento("Secreto User A", new BigDecimal("100.00"), new BigDecimal("1000.00"), idUserA);

        mockMvc.perform(get("/api/investimentos")
                .header("Authorization", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void naoDeveDepositarEmInvestimentoDeOutroUsuario() throws Exception {
        String tokenB = "Bearer " + setupUsuarioVerificado("User Bee", "userb2@email.com", "Senha@123");
        Long idUserB = usuarioRepository.findByEmail("userb2@email.com").get().getId();
        
        Long idContaB = criarConta("Conta B", new BigDecimal("1000.00"), idUserB);
        Long idInvA = salvarInvestimento("Investimento A", new BigDecimal("100.00"), new BigDecimal("1000.00"), idUserA);

        mockMvc.perform(post("/api/investimentos/" + idInvA + "/depositar")
                .header("Authorization", tokenB)
                .param("valor", "100.00")
                .param("contaId", idContaB.toString()))
                .andExpect(status().isNotFound());
    }
}
