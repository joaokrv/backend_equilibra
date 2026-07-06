package org.app_financeiro.backend;

import org.app_financeiro.backend.dto.request.ConfirmarAcaoContaRequestDTO;
import org.app_financeiro.backend.dto.request.SolicitarAcaoContaRequestDTO;
import org.app_financeiro.backend.entity.CodigoVerificacaoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoCodigoVerificacao;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração para exclusão permanente de conta.
 * <p>
 * Cobre dois eixos:
 * - CASCADE DB (V48): verifica que DELETE em usuarios remove filhos via ON DELETE CASCADE,
 *   incluindo notificacao_fatura e movimentacao_investimento adicionados após V36.
 * - Fluxo completo de endpoint: registro → OTP → exclusão → verificação no banco.
 */
class ExclusaoContaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String EMAIL = "excluir@teste.com";
    private static final String SENHA = "Senha123!";

    @BeforeEach
    void setUp() {
        limparTodasAsTabelas();
        when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
    }

    /**
     * Valida a migration V48: ON DELETE CASCADE nas FKs de notificacao_fatura e
     * movimentacao_investimento. Insere dados diretamente via JDBC e confirma que
     * um DELETE em usuarios propaga a remoção sem DataIntegrityViolationException.
     */
    @Test
    void deveDeletarUsuarioCascateandoNotificacaoFaturaEMovimentacaoInvestimento() {
        // 1. Inserir usuário diretamente via JDBC
        jdbcTemplate.update("""
                INSERT INTO usuarios (nome, email, senha, email_verificado, ativo, moeda, login_attempts, notificacoes_fatura_ativo)
                VALUES ('Teste Cascade', ?, 'hash', true, true, 'BRL', 0, true)
                """, EMAIL);
        Long usuarioId = jdbcTemplate.queryForObject("SELECT id FROM usuarios WHERE email = ?", Long.class, EMAIL);

        // 2. Montar cadeia mínima para notificacao_fatura: cartao → fatura
        jdbcTemplate.update("""
                INSERT INTO cartoes (nome, limite, dia_vencimento, dia_fechamento, usuario_id)
                VALUES ('Cartao', 1000, 10, 5, ?)
                """, usuarioId);
        Long cartaoId = jdbcTemplate.queryForObject("SELECT id FROM cartoes WHERE usuario_id = ?", Long.class, usuarioId);

        jdbcTemplate.update("""
                INSERT INTO faturas (cartao_id, usuario_id, mes, ano, status, data_fechamento, data_vencimento)
                VALUES (?, ?, 7, 2026, 'ABERTA', CURRENT_DATE + 30, CURRENT_DATE + 37)
                """, cartaoId, usuarioId);
        Long faturaId = jdbcTemplate.queryForObject("SELECT id FROM faturas WHERE usuario_id = ?", Long.class, usuarioId);

        jdbcTemplate.update("""
                INSERT INTO notificacao_fatura (fatura_id, usuario_id, tipo, scheduled_at, status, idempotency_key, data_criacao)
                VALUES (?, ?, 'VENCIMENTO', CURRENT_DATE, 'PENDENTE', 'key-cascade-v48-notif', NOW())
                """, faturaId, usuarioId);

        // 3. Montar cadeia mínima para movimentacao_investimento: investimento
        jdbcTemplate.update("""
                INSERT INTO investimentos (descricao, valor_inicial, valor_atual, usuario_id)
                VALUES ('Inv Cascade', 100, 100, ?)
                """, usuarioId);
        Long investimentoId = jdbcTemplate.queryForObject("SELECT id FROM investimentos WHERE usuario_id = ?", Long.class, usuarioId);

        jdbcTemplate.update("""
                INSERT INTO movimentacao_investimento (investimento_id, usuario_id, tipo, valor, data, ativo, data_criacao, data_atualizacao)
                VALUES (?, ?, 'APORTE', 100, CURRENT_DATE, true, NOW(), NOW())
                """, investimentoId, usuarioId);

        // 4. Confirmar que os dados problemáticos existem antes do delete
        assertThat(contarLinhas("notificacao_fatura", "usuario_id", usuarioId)).isEqualTo(1);
        assertThat(contarLinhas("movimentacao_investimento", "usuario_id", usuarioId)).isEqualTo(1);

        // 5. Deletar o usuário — antes da V48 isso lançava DataIntegrityViolationException
        jdbcTemplate.update("DELETE FROM usuarios WHERE id = ?", usuarioId);

        // 6. Verificar CASCADE: nenhuma linha órfã deve existir
        assertThat(contarLinhas("usuarios", "id", usuarioId)).isZero();
        assertThat(contarLinhas("notificacao_fatura", "usuario_id", usuarioId)).isZero();
        assertThat(contarLinhas("movimentacao_investimento", "usuario_id", usuarioId)).isZero();
        assertThat(contarLinhas("faturas", "usuario_id", usuarioId)).isZero();
        assertThat(contarLinhas("cartoes", "usuario_id", usuarioId)).isZero();
        assertThat(contarLinhas("investimentos", "usuario_id", usuarioId)).isZero();
    }

    /**
     * Fluxo completo via endpoint: registro → solicitar OTP → excluir conta.
     * Confirma que DELETE /api/auth/excluir-conta retorna 200 e o usuário
     * é removido permanentemente do banco.
     */
    @Test
    void deveExcluirContaViaEndpointComSucesso() throws Exception {
        String token = setupUsuarioVerificado("Excluir Teste", EMAIL, SENHA);

        UsuarioEntity usuario = usuarioRepository.findByEmail(EMAIL)
                .orElseThrow(() -> new AssertionError("Usuário não encontrado após registro"));
        Long usuarioId = usuario.getId();

        // Solicitar OTP de exclusão
        SolicitarAcaoContaRequestDTO solicitarDto = new SolicitarAcaoContaRequestDTO("EXCLUIR", SENHA);
        mockMvc.perform(post("/api/auth/solicitar-acao-conta")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitarDto)))
                .andExpect(status().isOk());

        // Interceptar o código OTP gerado
        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository.findAll().stream()
                .filter(c -> c.getEmail().equals(EMAIL)
                        && c.getTipo() == TipoCodigoVerificacao.EXCLUSAO_CONTA
                        && !c.isUtilizado())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Código OTP de exclusão não encontrado"));

        // Confirmar exclusão com senha + OTP
        ConfirmarAcaoContaRequestDTO confirmarDto = new ConfirmarAcaoContaRequestDTO(SENHA, codigoEntity.getCodigo());
        mockMvc.perform(delete("/api/auth/excluir-conta")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmarDto)))
                .andExpect(status().isOk());

        // Verificar que o usuário foi deletado (bypassa @SQLRestriction com JDBC)
        assertThat(contarLinhas("usuarios", "id", usuarioId)).isZero();
    }

    /**
     * Garante que senha inválida retorna 401 e o usuário não é deletado.
     */
    @Test
    void deveRejeitarExclusaoComSenhaInvalida() throws Exception {
        String token = setupUsuarioVerificado("Excluir Teste", EMAIL, SENHA);

        // Solicitar OTP legítimo
        SolicitarAcaoContaRequestDTO solicitarDto = new SolicitarAcaoContaRequestDTO("EXCLUIR", SENHA);
        mockMvc.perform(post("/api/auth/solicitar-acao-conta")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitarDto)))
                .andExpect(status().isOk());

        CodigoVerificacaoEntity codigoEntity = codigoVerificacaoRepository.findAll().stream()
                .filter(c -> c.getEmail().equals(EMAIL)
                        && c.getTipo() == TipoCodigoVerificacao.EXCLUSAO_CONTA
                        && !c.isUtilizado())
                .findFirst()
                .orElseThrow();

        // Tentar excluir com senha errada
        ConfirmarAcaoContaRequestDTO confirmarDto = new ConfirmarAcaoContaRequestDTO("SenhaErrada1!", codigoEntity.getCodigo());
        mockMvc.perform(delete("/api/auth/excluir-conta")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmarDto)))
                .andExpect(status().isUnauthorized());

        // Usuário deve continuar existindo
        assertThat(usuarioRepository.findByEmail(EMAIL)).isPresent();
    }

    private int contarLinhas(String tabela, String coluna, Long valor) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tabela + " WHERE " + coluna + " = ?",
                Integer.class, valor);
        return count != null ? count : 0;
    }
}
