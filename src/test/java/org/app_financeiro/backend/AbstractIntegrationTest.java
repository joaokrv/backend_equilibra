package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.VerificarEmailRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioPendenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Classe base para todos os testes de integração.
 * Sobe um banco de dados real (PostgreSQL) num container temporário via Testcontainers.
 * Inclui helper reutilizável para o fluxo pré-registrar → verificar OTP → login.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @MockBean
    protected JavaMailSender mailSender;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected CodigoVerificacaoRepository codigoVerificacaoRepository;

    @Autowired
    protected UsuarioPendenteRepository usuarioPendenteRepository;

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("equilibra_test")
            .withUsername("test")
            .withPassword("test");

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

        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
