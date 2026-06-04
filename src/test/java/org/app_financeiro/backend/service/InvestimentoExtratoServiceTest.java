package org.app_financeiro.backend.service;

import org.app_financeiro.backend.AbstractIntegrationTest;
import org.app_financeiro.backend.dto.request.RendimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.MovimentacaoAtualizacaoRequestDTO;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.TipoInvestimento;
import org.app_financeiro.backend.enums.TipoMovimentacaoInvestimento;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.MovimentacaoInvestimentoRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Sem @Transactional na classe: deleteAllInBatch() commita imediatamente,
// tornando a limpeza visível para as chamadas MockMvc de setupUsuarioVerificado().
class InvestimentoExtratoServiceTest extends AbstractIntegrationTest {

    @Autowired private InvestimentoService investimentoService;
    @Autowired private InvestimentoRepository investimentoRepository;
    @Autowired private MovimentacaoInvestimentoRepository movimentacaoRepository;
    @Autowired private TransacaoRepository transacaoRepository;
    @Autowired private ContaRepository contaRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Long usuarioId;
    private Long contaId;
    private Long investimentoId;

    @BeforeEach
    void setUp() throws Exception {
        limparTodasAsTabelas();

        setupUsuarioVerificado("João Extrato", "extrato@teste.com", "Senha@123");
        usuarioId = usuarioRepository.findByEmail("extrato@teste.com").orElseThrow().getId();

        ContaEntity conta = new ContaEntity();
        conta.setNome("Conta Teste");
        conta.setSaldo(new BigDecimal("10000.00"));
        conta.setAtivo(true);
        conta.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        contaId = contaRepository.saveAndFlush(conta).getId();

        InvestimentoEntity inv = new InvestimentoEntity();
        inv.setDescricao("CDB XP");
        inv.setValorInicial(BigDecimal.ZERO);
        inv.setValorAtual(BigDecimal.ZERO);
        inv.setTipoInvestimento(TipoInvestimento.CDB);
        inv.setAtivo(true);
        inv.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        investimentoId = investimentoRepository.saveAndFlush(inv).getId();
    }

    // ── Rendimento ────────────────────────────────────────────────────────────

    @Test
    void deveRegistrarRendimentoPositivo() {
        var dto = new RendimentoRegistroRequestDTO(investimentoId, new BigDecimal("50.00"), LocalDate.now(), "Juros mês");
        investimentoService.registrarRendimento(dto, usuarioId);

        var inv = investimentoRepository.findById(investimentoId).orElseThrow();
        assertThat(inv.getValorAtual()).isEqualByComparingTo("50.00");

        var movs = movimentacaoRepository.findAll();
        assertThat(movs).hasSize(1);
        assertThat(movs.get(0).getTipo()).isEqualTo(TipoMovimentacaoInvestimento.RENDIMENTO);
        assertThat(movs.get(0).getTransacaoId()).isNull();
        assertThat(movs.get(0).getContaId()).isNull();
    }

    @Test
    void deveRegistrarRendimentoNegativo() {
        // Primeiro aporte para ter saldo
        investimentoService.adicionarDeposito(investimentoId, new BigDecimal("200.00"), contaId, usuarioId);

        var dto = new RendimentoRegistroRequestDTO(investimentoId, new BigDecimal("-30.00"), LocalDate.now(), "Queda de valor");
        investimentoService.registrarRendimento(dto, usuarioId);

        var inv = investimentoRepository.findById(investimentoId).orElseThrow();
        assertThat(inv.getValorAtual()).isEqualByComparingTo("170.00");
    }

    @Test
    void deveEditarRendimento() {
        var dto = new RendimentoRegistroRequestDTO(investimentoId, new BigDecimal("100.00"), LocalDate.now(), null);
        var criado = investimentoService.registrarRendimento(dto, usuarioId);

        var editDto = new MovimentacaoAtualizacaoRequestDTO(new BigDecimal("150.00"), LocalDate.now(), null, "Ajuste");
        investimentoService.editarMovimentacao(criado.id(), editDto, usuarioId);

        var inv = investimentoRepository.findById(investimentoId).orElseThrow();
        assertThat(inv.getValorAtual()).isEqualByComparingTo("150.00");
    }

    @Test
    void deveExcluirRendimento() {
        var dto = new RendimentoRegistroRequestDTO(investimentoId, new BigDecimal("80.00"), LocalDate.now(), null);
        var criado = investimentoService.registrarRendimento(dto, usuarioId);

        investimentoService.excluirMovimentacao(criado.id(), usuarioId);

        var inv = investimentoRepository.findById(investimentoId).orElseThrow();
        assertThat(inv.getValorAtual()).isEqualByComparingTo("0.00");

        var mov = movimentacaoRepository.findById(criado.id()).orElseThrow();
        assertThat(mov.isAtivo()).isFalse();
    }

    // ── Aporte ────────────────────────────────────────────────────────────────

    @Test
    void deveGravarMovimentacaoAoAportar() {
        investimentoService.adicionarDeposito(investimentoId, new BigDecimal("300.00"), contaId, usuarioId);

        var movs = movimentacaoRepository.findAll();
        assertThat(movs).hasSize(1);
        assertThat(movs.get(0).getTipo()).isEqualTo(TipoMovimentacaoInvestimento.APORTE);
        assertThat(movs.get(0).getTransacaoId()).isNotNull();
        assertThat(movs.get(0).getContaId()).isEqualTo(contaId);
    }

    @Test
    void deveExcluirAporteRevertendoContaEValorAtual() {
        investimentoService.adicionarDeposito(investimentoId, new BigDecimal("500.00"), contaId, usuarioId);
        var saldoAposAporte = contaRepository.findById(contaId).orElseThrow().getSaldo();

        var movId = movimentacaoRepository.findAll().get(0).getId();
        investimentoService.excluirMovimentacao(movId, usuarioId);

        var inv = investimentoRepository.findById(investimentoId).orElseThrow();
        assertThat(inv.getValorAtual()).isEqualByComparingTo("0.00");

        var contaApos = contaRepository.findById(contaId).orElseThrow();
        assertThat(contaApos.getSaldo()).isGreaterThan(saldoAposAporte);
    }

    // ── Resgate ───────────────────────────────────────────────────────────────

    @Test
    void deveGravarMovimentacaoAoResgatar() {
        investimentoService.adicionarDeposito(investimentoId, new BigDecimal("1000.00"), contaId, usuarioId);
        movimentacaoRepository.deleteAll(); // limpa aporte para testar só o resgate

        investimentoService.resgatarInvestimento(investimentoId, new BigDecimal("400.00"), contaId, usuarioId);

        var movs = movimentacaoRepository.findAll();
        assertThat(movs).hasSize(1);
        assertThat(movs.get(0).getTipo()).isEqualTo(TipoMovimentacaoInvestimento.RESGATE);
    }

    @Test
    void deveExcluirResgateRevertendoContaEValorAtual() {
        investimentoService.adicionarDeposito(investimentoId, new BigDecimal("1000.00"), contaId, usuarioId);
        investimentoService.resgatarInvestimento(investimentoId, new BigDecimal("300.00"), contaId, usuarioId);

        var movResgate = movimentacaoRepository.findAll().stream()
                .filter(m -> m.getTipo() == TipoMovimentacaoInvestimento.RESGATE)
                .findFirst().orElseThrow();

        var saldoAntes = contaRepository.findById(contaId).orElseThrow().getSaldo();

        investimentoService.excluirMovimentacao(movResgate.getId(), usuarioId);

        var inv = investimentoRepository.findById(investimentoId).orElseThrow();
        assertThat(inv.getValorAtual()).isEqualByComparingTo("1000.00");

        var saldoApos = contaRepository.findById(contaId).orElseThrow().getSaldo();
        assertThat(saldoApos).isLessThan(saldoAntes); // dinheiro saiu de volta da conta
    }

    // ── Extrato paginado ──────────────────────────────────────────────────────

    @Test
    void deveListarExtratoPaginado() {
        investimentoService.adicionarDeposito(investimentoId, new BigDecimal("100.00"), contaId, usuarioId);
        investimentoService.registrarRendimento(
                new RendimentoRegistroRequestDTO(investimentoId, new BigDecimal("5.00"), LocalDate.now(), null),
                usuarioId);

        var pageable = PageRequest.of(0, 10);
        var page = investimentoService.listarMovimentacoes(usuarioId, null, null, null, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).investimentoId()).isEqualTo(investimentoId);
    }

    @Test
    void deveFiltraExtratoPorTipo() {
        investimentoService.adicionarDeposito(investimentoId, new BigDecimal("100.00"), contaId, usuarioId);
        investimentoService.registrarRendimento(
                new RendimentoRegistroRequestDTO(investimentoId, new BigDecimal("5.00"), LocalDate.now(), null),
                usuarioId);

        var pageable = PageRequest.of(0, 10);
        var page = investimentoService.listarMovimentacoes(
                usuarioId, TipoMovimentacaoInvestimento.RENDIMENTO, null, null, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).tipo()).isEqualTo(TipoMovimentacaoInvestimento.RENDIMENTO);
    }

    // ── IDOR ─────────────────────────────────────────────────────────────────

    @Test
    void naoDeveAcessarMovimentacaoDeOutroUsuario() throws Exception {
        investimentoService.adicionarDeposito(investimentoId, new BigDecimal("100.00"), contaId, usuarioId);
        var movId = movimentacaoRepository.findAll().get(0).getId();

        setupUsuarioVerificado("Outro User", "outro@teste.com", "Senha@123");
        Long outroUsuarioId = usuarioRepository.findByEmail("outro@teste.com").orElseThrow().getId();

        assertThatThrownBy(() -> investimentoService.excluirMovimentacao(movId, outroUsuarioId))
                .hasMessageContaining("não encontrada");
    }
}
