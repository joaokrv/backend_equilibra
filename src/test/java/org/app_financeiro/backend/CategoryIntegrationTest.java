package org.app_financeiro.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.app_financeiro.backend.dto.request.CategoriaRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioLoginRequestDTO;
import org.app_financeiro.backend.dto.request.UsuarioRegistroRequestDTO;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.repository.CategoriaRepository;
import org.app_financeiro.backend.repository.CodigoVerificacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class CategoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private CodigoVerificacaoRepository codigoVerificacaoRepository;

    private String tokenA;
    private Long idUserA;

    @BeforeEach
    void setUp() throws Exception {
        // Mock MailSender to avoid connection errors during registration
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        // Limpar dados antes de cada teste (ordem inversa de dependência)
        categoriaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();

        // Registrar, verificar e logar Usuário A
        tokenA = "Bearer " + setupUser("User A", "userA@email.com");
        idUserA = usuarioRepository.findByEmail("userA@email.com").get().getId();
    }

    private String setupUser(String nome, String email) throws Exception {
        UsuarioRegistroRequestDTO reg = new UsuarioRegistroRequestDTO(nome, email, "senha123");
        mockMvc.perform(post("/api/auth/registrar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        // Forçar verificação de e-mail no banco
        UsuarioEntity user = usuarioRepository.findByEmail(email).orElseThrow();
        user.setEmailVerificado(true);
        usuarioRepository.save(user);

        // Login
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UsuarioLoginRequestDTO(email, "senha123"))))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> response = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        return (String) response.get("accessToken");
    }

    private void salvarCategoria(String nome, TipoTransacao tipo, Long usuarioId) {
        CategoriaEntity cat = new CategoriaEntity();
        cat.setNome(nome);
        cat.setTipo(tipo);
        cat.setAtivo(true);
        cat.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        categoriaRepository.saveAndFlush(cat);
    }

    // =============================================
    // TESTES DE SUCESSO
    // =============================================

    @Test
    void deveCriarCategoriaComSucesso() throws Exception {
        CategoriaRegistroRequestDTO dto = new CategoriaRegistroRequestDTO("Alimentação", TipoTransacao.DESPESA);

        mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Alimentação"))
                .andExpect(jsonPath("$.tipo").value("DESPESA"));

        assertThat(categoriaRepository.count()).isEqualTo(1);
    }

    @Test
    void devePermitirMesmoNomeTiposDiferentes() throws Exception {
        CategoriaRegistroRequestDTO despesa = new CategoriaRegistroRequestDTO("Lazer", TipoTransacao.DESPESA);
        CategoriaRegistroRequestDTO receita = new CategoriaRegistroRequestDTO("Lazer", TipoTransacao.RECEITA);

        mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(despesa)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(receita)))
                .andExpect(status().isCreated());

        assertThat(categoriaRepository.count()).isEqualTo(2);
    }

    @Test
    void deveListarCategoriasDoUsuario() throws Exception {
        salvarCategoria("Cat 1", TipoTransacao.DESPESA, idUserA);
        salvarCategoria("Cat 2", TipoTransacao.RECEITA, idUserA);

        mockMvc.perform(get("/api/categorias")
                .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void deveFiltrarCategoriasPorTipo() throws Exception {
        salvarCategoria("Despesa 1", TipoTransacao.DESPESA, idUserA);
        salvarCategoria("Receita 1", TipoTransacao.RECEITA, idUserA);

        mockMvc.perform(get("/api/categorias")
                .param("tipo", "DESPESA")
                .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("DESPESA"));
    }

    @Test
    void deveDeletarCategoriaComSucesso() throws Exception {
        CategoriaRegistroRequestDTO dto = new CategoriaRegistroRequestDTO("Para Deletar", TipoTransacao.DESPESA);
        MvcResult result = mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        
        Long idCat = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/categorias/" + idCat)
                .header("Authorization", tokenA))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        assertThat(categoriaRepository.findById(idCat)).isEmpty();
    }

    // =============================================
    // TESTES DE ERRO E VALIDAÇÃO
    // =============================================

    @Test
    void naoDevePermitirCategoriaDuplicadaMesmoTipo() throws Exception {
        CategoriaRegistroRequestDTO dto = new CategoriaRegistroRequestDTO("Alimentação", TipoTransacao.DESPESA);

        mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict());
    }

    @Test
    void naoDeveCriarCategoriaComNomeEmBranco() throws Exception {
        CategoriaRegistroRequestDTO dto = new CategoriaRegistroRequestDTO("", TipoTransacao.DESPESA);

        mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnprocessableEntity()) // 422 - Validation error
                .andExpect(jsonPath("$.erro").value("Erro de validação"));
    }

    @Test
    void naoDeveCriarCategoriaComTipoNulo() throws Exception {
        // Usando Map pois o record não permite nulo no enum se serializado via objectMapper as vezes,
        // mas aqui queremos testar a validação do Bean
        Map<String, Object> body = Map.of("nome", "Teste", "tipo", ""); // Tipo inválido/nulo

        mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()); // Jackson error before bean validation usually
    }

    // =============================================
    // TESTES DE SEGURANÇA E ISOLAMENTO
    // =============================================

    @Test
    void naoDeveDeletarCategoriaDeOutroUsuario() throws Exception {
        String tokenB = "Bearer " + setupUser("User B", "userB@email.com");

        CategoriaRegistroRequestDTO dto = new CategoriaRegistroRequestDTO("Cat A", TipoTransacao.DESPESA);
        MvcResult resA = mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        
        Long idCatA = objectMapper.readTree(resA.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/categorias/" + idCatA)
                .header("Authorization", tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void usuarioBNaoDeveVerCategoriasDoUsuarioA() throws Exception {
        String tokenB = "Bearer " + setupUser("User B", "userB@email.com");

        salvarCategoria("Cat A 1", TipoTransacao.DESPESA, idUserA);
        salvarCategoria("Cat A 2", TipoTransacao.RECEITA, idUserA);

        mockMvc.perform(get("/api/categorias")
                .header("Authorization", tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0)); // Usuário B não tem nada
    }

    @Test
    void softDeleteDeveRemoverDaListagem() throws Exception {
        CategoriaRegistroRequestDTO dto = new CategoriaRegistroRequestDTO("Temp", TipoTransacao.DESPESA);
        MvcResult res = mockMvc.perform(post("/api/categorias")
                .header("Authorization", tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();
        
        Long idCat = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Antes do delete: length = 1
        mockMvc.perform(get("/api/categorias").header("Authorization", tokenA))
                .andExpect(jsonPath("$.length()").value(1));

        // Delete
        mockMvc.perform(delete("/api/categorias/" + idCat).header("Authorization", tokenA))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();

        // Após o delete: length = 0 (devido ao @SQLRestriction)
        mockMvc.perform(get("/api/categorias").header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
