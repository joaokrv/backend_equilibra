package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.MovimentacaoInvestimentoRepository;
import org.app_financeiro.backend.repository.NotificacaoFaturaRepository;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.app_financeiro.backend.service.ExternalEmailSenderService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Classe base para todos os testes de integração.
 * <p>
 * Usa o <b>Singleton Container Pattern</b> do Testcontainers: o container PostgreSQL sobe
 * uma única vez por JVM e é compartilhado por todas as subclasses. Sem {@code @Container}
 * nem {@code @Testcontainers} — caso contrário, cada classe destruiria o container ao
 * terminar e a próxima classe falharia com "Connection refused" contra a porta antiga
 * (o Spring cacheia contextos entre classes mas o pool Hikari aponta para a porta original).
 * <p>
 * O {@code Ryuk} do Testcontainers garante o cleanup automático no shutdown da JVM.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @MockBean
    protected JavaMailSender mailSender;

    @MockBean
    protected ExternalEmailSenderService externalEmailSenderService;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Autowired
    protected UsuarioPendenteRepository usuarioPendenteRepository;

    /**
     * Tabelas com FK sem CASCADE criadas nesta feature.
     * Precisam ser limpas ANTES das tabelas que referenciam (investimentos, transacoes, faturas, contas, usuarios).
     * Subclasses devem chamar estas deleções no início do setUp(), antes de deletar as tabelas pai.
     */
    @Autowired
    protected MovimentacaoInvestimentoRepository movimentacaoInvestimentoRepository;

    @Autowired
    protected NotificacaoFaturaRepository notificacaoFaturaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Limpeza completa usando SQL nativo — bypassa @SQLRestriction("ativo = true") e
     * respeita a ordem de FK. Necessário porque soft-delete (ativo=false) não é removido
     * pelos métodos JPA que respeitam a restrição de filtro.
     */
    protected void limparTodasAsTabelas() {
        jdbcTemplate.execute("DELETE FROM movimentacao_investimento");
        jdbcTemplate.execute("DELETE FROM notificacao_fatura");
        jdbcTemplate.execute("DELETE FROM transacoes");
        jdbcTemplate.execute("DELETE FROM transacoes_recorrentes");
        jdbcTemplate.execute("DELETE FROM faturas");
        jdbcTemplate.execute("DELETE FROM investimentos");
        jdbcTemplate.execute("DELETE FROM cartoes");
        jdbcTemplate.execute("DELETE FROM contas");
        jdbcTemplate.execute("DELETE FROM categorias");
        jdbcTemplate.execute("DELETE FROM codigos_verificacao");
        jdbcTemplate.execute("DELETE FROM usuarios_pendentes");
        jdbcTemplate.execute("DELETE FROM patrimonio_historico");
        jdbcTemplate.execute("DELETE FROM usuarios");
    }

    protected static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("equilibra_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }


    /**
     * Fluxo completo de registro: pré-registrar → verificar OTP → usuário verificado.
     * Retorna o accessToken para uso nos testes.
     */
    protected String setupUsuarioVerificado(String nome, String email, String senha) throws Exception {
        UsuarioRegistroRequestDTO reg = new UsuarioRegistroRequestDTO(nome, email, senha);
        MvcResult preRegistro = mockMvc.perform(post("/api/auth/pre-registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andReturn();

        String registroId = objectMapper.readTree(preRegistro.getResponse().getContentAsString())
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

        UsuarioLoginRequestDTO login = new UsuarioLoginRequestDTO(email, senha);
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        // Access token agora vem em httpOnly cookie (não em JSON)
        var accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
        if (accessTokenCookie != null && accessTokenCookie.getValue() != null) {
            return accessTokenCookie.getValue();
        }
        throw new IllegalStateException("Access token cookie não encontrado na resposta de login");
    }
}
