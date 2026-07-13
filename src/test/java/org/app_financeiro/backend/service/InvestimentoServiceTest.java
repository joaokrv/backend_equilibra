package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.dto.response.TransacaoResponseDTO;
import org.app_financeiro.backend.entity.MovimentacaoInvestimentoEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoInvestimento;
import org.app_financeiro.backend.enums.TipoMovimentacaoInvestimento;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.exception.SaldoInsuficienteException;
import org.app_financeiro.backend.mapper.InvestimentoMapper;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.MovimentacaoInvestimentoRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestimentoServiceTest {

    @Mock
    private InvestimentoRepository investimentoRepository;

    @Mock
    private ContaService contaService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private InvestimentoMapper investimentoMapper;

    @Mock
    private TransacaoService transacaoService;

    @Mock
    private PatrimonioHistoricoService patrimonioHistoricoService;

    @Mock
    private MovimentacaoInvestimentoRepository movimentacaoInvestimentoRepository;

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private MovimentacaoFinanceiraService movimentacaoFinanceiraService;

    /** Self-referência via proxy — necessária para excluirMovimentacoesEmMassa chamar excluirMovimentacao respeitando @Transactional. */
    @Mock
    private InvestimentoService self;

    @InjectMocks
    private InvestimentoService investimentoService;

    private static final TransacaoResponseDTO TRANSACAO_MOCK = new TransacaoResponseDTO(
        99L, "Aporte", BigDecimal.TEN, LocalDate.now(),
        TipoTransacao.DESPESA, StatusTransacao.PAGO, MetodoPagamento.TRANSFERENCIA,
        null, null, null, null, null, null, false, null, null, true);

    private UsuarioEntity usuarioPadrao;
    private InvestimentoEntity investimentoPadrao;

    @BeforeEach
    void setUp() {
        lenient().when(movimentacaoInvestimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        usuarioPadrao = new UsuarioEntity();
        usuarioPadrao.setId(1L);
        usuarioPadrao.setNome("Joao");

        investimentoPadrao = new InvestimentoEntity();
        investimentoPadrao.setId(10L);
        investimentoPadrao.setDescricao("Viagem Japão");
        investimentoPadrao.setValorInicial(new BigDecimal("500.00"));
        investimentoPadrao.setValorAtual(new BigDecimal("1500.00"));
        investimentoPadrao.setMetaAtual(new BigDecimal("10000.00"));
        investimentoPadrao.setTipoInvestimento(TipoInvestimento.CDB);
        investimentoPadrao.setUsuario(usuarioPadrao);
        investimentoPadrao.setAtivo(true);
    }

    @Test
    void deveCriarInvestimentoComSucesso() {
        InvestimentoRegistroRequestDTO request = new InvestimentoRegistroRequestDTO(
            "Viagem Japão", new BigDecimal("500.00"), new BigDecimal("10000.00"), 1L, null,
            TipoInvestimento.CDB, null);

        InvestimentoResponseDTO responseEsperada = new InvestimentoResponseDTO(
            10L, "Viagem Japão", TipoInvestimento.CDB, null, new BigDecimal("500.00"), new BigDecimal("500.00"), new BigDecimal("10000.00"), "Conta Teste", null);

        ContaEntity contaMock = new ContaEntity();
        contaMock.setId(1L);
        contaMock.setNome("Conta Teste");

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(contaService.buscarContaValidada(1L, 1L)).thenReturn(contaMock);
        when(transacaoService.criarTransacaoInterna(any(TransacaoRegistroRequestDTO.class), eq(1L), eq(true))).thenReturn(TRANSACAO_MOCK);
        when(investimentoRepository.save(any())).thenAnswer(i -> {
            InvestimentoEntity inv = i.getArgument(0);
            inv.setId(10L);
            return inv;
        });

        when(investimentoMapper.toResponse(any())).thenReturn(responseEsperada);

        InvestimentoResponseDTO result = investimentoService.criarInvestimento(request, 1L);

        assertThat(result.descricao()).isEqualTo("Viagem Japão");
        assertThat(result.valorAtual()).isEqualTo(new BigDecimal("500.00"));
        verify(investimentoRepository).save(any());
        verify(transacaoService).criarTransacaoInterna(any(TransacaoRegistroRequestDTO.class), eq(1L), eq(true));
        verify(patrimonioHistoricoService).atualizarSnapshotUsuarioHoje(1L);
    }

    @Test
    void deveDepositarNoInvestimentoComSucesso() {
        BigDecimal deposito = new BigDecimal("200.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(TransacaoRegistroRequestDTO.class), eq(1L), eq(true))).thenReturn(TRANSACAO_MOCK);

        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InvestimentoResponseDTO responseEsperada = new InvestimentoResponseDTO(
            10L, "Viagem Japão", TipoInvestimento.CDB, null, new BigDecimal("500.00"), new BigDecimal("1700.00"), new BigDecimal("10000.00"), null, null);
        when(investimentoMapper.toResponse(any())).thenReturn(responseEsperada);

        InvestimentoResponseDTO result = investimentoService.adicionarDeposito(10L, deposito, 5L, 1L);

        assertThat(result.valorAtual()).isEqualTo(new BigDecimal("1700.00"));
        assertThat(investimentoPadrao.getValorAtual()).isEqualByComparingTo(new BigDecimal("1700.00"));

        verify(transacaoService).criarTransacaoInterna(any(TransacaoRegistroRequestDTO.class), eq(1L), eq(true));
        verify(investimentoRepository).save(investimentoPadrao);
        verify(patrimonioHistoricoService).atualizarSnapshotUsuarioHoje(1L);
    }

    @Test
    void deveFalharODepositoSeAcontaNaoTiverSaldo() {
        BigDecimal deposito = new BigDecimal("20000.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(TransacaoRegistroRequestDTO.class), eq(1L), eq(true)))
                .thenThrow(new SaldoInsuficienteException("Saldo INSUFICIENTE"));

        assertThatThrownBy(() -> investimentoService.adicionarDeposito(10L, deposito, 5L, 1L))
                .isInstanceOf(SaldoInsuficienteException.class);

        verify(investimentoRepository, never()).save(any());
    }

    @Test
    void deveResgatarDoInvestimentoComSucesso() {
        BigDecimal resgate = new BigDecimal("500.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(TransacaoRegistroRequestDTO.class), eq(1L), eq(true))).thenReturn(TRANSACAO_MOCK);

        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        InvestimentoResponseDTO responseEsperada = new InvestimentoResponseDTO(
            10L, "Viagem Japão", TipoInvestimento.CDB, null, new BigDecimal("500.00"), new BigDecimal("1000.00"), new BigDecimal("10000.00"), null, null);
        when(investimentoMapper.toResponse(any())).thenReturn(responseEsperada);

        InvestimentoResponseDTO result = investimentoService.resgatarInvestimento(10L, resgate, 5L, 1L);

        assertThat(result.valorAtual()).isEqualTo(new BigDecimal("1000.00"));
        verify(transacaoService).criarTransacaoInterna(any(TransacaoRegistroRequestDTO.class), eq(1L), eq(true));
        verify(investimentoRepository).save(investimentoPadrao);
        verify(patrimonioHistoricoService).atualizarSnapshotUsuarioHoje(1L);
    }

    @Test
    void deveLancarExcecaoGraveSeTentarResgatarMaisQueOValorDessaMetaPoupanca() {
        BigDecimal resgateAlemDoLimite = new BigDecimal("2000.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));

        assertThatThrownBy(() -> investimentoService.resgatarInvestimento(10L, resgateAlemDoLimite, 5L, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("excede o saldo do investimento");

        verify(transacaoService, never()).criarTransacaoInterna(any(TransacaoRegistroRequestDTO.class), anyLong(), anyBoolean());
        verify(investimentoRepository, never()).save(any());
    }

    @Test
    void deveAtualizarMetaComSucesso() {
        BigDecimal novaMeta = new BigDecimal("15000.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InvestimentoResponseDTO responseEsperada = new InvestimentoResponseDTO(
            10L, "Viagem Japão", TipoInvestimento.CDB, null, new BigDecimal("500.00"), new BigDecimal("1500.00"), new BigDecimal("15000.00"), null, null);
        when(investimentoMapper.toResponse(any())).thenReturn(responseEsperada);

        InvestimentoResponseDTO result = investimentoService.atualizarMeta(10L, novaMeta, 1L);

        assertThat(result.metaAtual()).isEqualByComparingTo(new BigDecimal("15000.00"));
        verify(investimentoRepository).save(investimentoPadrao);
    }

    @Test
    void deveLancarExcecaoSeTentarDefinirAMetaZeradaOuNegativa() {
        BigDecimal novaMeta = new BigDecimal("-100.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));

        assertThatThrownBy(() -> investimentoService.atualizarMeta(10L, novaMeta, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("meta deve ser maior que zero");
    }

    @Test
    void deveDeletarOInvestimentoApenasSeEleEstiverZeradinhoSemSaldo() {
        investimentoPadrao.setValorAtual(BigDecimal.ZERO);
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        investimentoService.deletarInvestimento(10L, 1L);

        assertThat(investimentoPadrao.isAtivo()).isFalse();
        verify(investimentoRepository).save(investimentoPadrao);
    }

    @Test
    void deveBloquearTentativaDoMelianteDeDeleterAContaComOSaldoNeleOuAborrecidoDesativar() {
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));

        assertThatThrownBy(() -> investimentoService.deletarInvestimento(10L, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Resgate o saldo restante");

        verify(investimentoRepository, never()).save(any());
    }

    @Test
    void buscarTodosDoUsuarioRetornaVazIOSeSoZerarOuNaoExistir() {
        when(investimentoRepository.findByUsuarioId(1L)).thenReturn(List.of(investimentoPadrao));

        List<InvestimentoResponseDTO> result = investimentoService.buscarTodosDoUsuario(1L);

        assertThat(result).hasSize(1);
    }

    // ─── aportarImportado / resgatarImportado (Fase 2) ────────────────────────

    @Test
    @DisplayName("aportarImportado usa a data do documento e a idempotencyKey fornecida — não LocalDate.now() nem UUID aleatório")
    void aportarImportadoUsaDataEChaveFornecidas() {
        LocalDate dataHistorica = LocalDate.of(2025, 3, 15);
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(TransacaoRegistroRequestDTO.class), eq(1L), eq(true)))
                .thenReturn(TRANSACAO_MOCK);
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Long transacaoId = investimentoService.aportarImportado(10L, new BigDecimal("200.00"), 5L, dataHistorica,
                "chave-deterministica-abc", true, 1L);

        ArgumentCaptor<TransacaoRegistroRequestDTO> captor = ArgumentCaptor.forClass(TransacaoRegistroRequestDTO.class);
        verify(transacaoService).criarTransacaoInterna(captor.capture(), eq(1L), eq(true));
        assertThat(captor.getValue().data()).isEqualTo(dataHistorica);
        assertThat(captor.getValue().idempotencyKey()).isEqualTo("chave-deterministica-abc");
        assertThat(captor.getValue().tipo()).isEqualTo(TipoTransacao.DESPESA);
        // Regressão: o processor depende deste retorno para marcar origem/importacaoId na transação.
        assertThat(transacaoId).isEqualTo(TRANSACAO_MOCK.id());
    }

    @Test
    @DisplayName("aportarImportado com atualizarValor=true soma ao valorAtual do investimento")
    void aportarImportadoComAtualizarValorVerdadeiroSomaValorAtual() {
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(), eq(1L), eq(true))).thenReturn(TRANSACAO_MOCK);
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        investimentoService.aportarImportado(10L, new BigDecimal("200.00"), 5L, LocalDate.now(),
                "key-1", true, 1L);

        assertThat(investimentoPadrao.getValorAtual()).isEqualByComparingTo(new BigDecimal("1700.00"));
        verify(investimentoRepository).save(investimentoPadrao);
    }

    @Test
    @DisplayName("aportarImportado com atualizarValor=false NÃO altera o valorAtual, mas cria a transação e a movimentação")
    void aportarImportadoComAtualizarValorFalsoNaoAlteraValorAtual() {
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(), eq(1L), eq(true))).thenReturn(TRANSACAO_MOCK);

        investimentoService.aportarImportado(10L, new BigDecimal("200.00"), 5L, LocalDate.now(),
                "key-2", false, 1L);

        assertThat(investimentoPadrao.getValorAtual()).isEqualByComparingTo(new BigDecimal("1500.00"));
        verify(investimentoRepository, never()).save(any());
        verify(movimentacaoInvestimentoRepository).save(any());
    }

    @Test
    @DisplayName("aportarImportado NÃO dispara snapshot de patrimônio — o lote da importação dispara uma única vez, ao final")
    void aportarImportadoNaoDisparaSnapshotPorItem() {
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(), eq(1L), eq(true))).thenReturn(TRANSACAO_MOCK);
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        investimentoService.aportarImportado(10L, new BigDecimal("200.00"), 5L, LocalDate.now(), "key-3", true, 1L);

        verifyNoInteractions(patrimonioHistoricoService);
    }

    @Test
    @DisplayName("aportarImportado sem contaId é rejeitado — aporte sempre precisa de uma conta de origem")
    void aportarImportadoSemContaIdEhRejeitado() {
        assertThatThrownBy(() -> investimentoService.aportarImportado(
                10L, new BigDecimal("200.00"), null, LocalDate.now(), "key-4", true, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Conta bancária");

        verifyNoInteractions(transacaoService);
    }

    @Test
    @DisplayName("SEGURANÇA (IDOR): aportarImportado em investimento de outro usuário é barrado")
    void aportarImportadoEmInvestimentoDeOutroUsuarioEhBarrado() {
        UsuarioEntity outroUsuario = new UsuarioEntity();
        outroUsuario.setId(2L);
        investimentoPadrao.setUsuario(outroUsuario);
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));

        assertThatThrownBy(() -> investimentoService.aportarImportado(
                10L, new BigDecimal("200.00"), 5L, LocalDate.now(), "key-5", true, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verifyNoInteractions(transacaoService);
    }

    @Test
    @DisplayName("resgatarImportado com atualizarValor=true subtrai do valorAtual")
    void resgatarImportadoComAtualizarValorVerdadeiroSubtraiValorAtual() {
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(), eq(1L), eq(true))).thenReturn(TRANSACAO_MOCK);
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        investimentoService.resgatarImportado(10L, new BigDecimal("500.00"), 5L, LocalDate.now(),
                "key-6", true, 1L);

        assertThat(investimentoPadrao.getValorAtual()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("resgatarImportado com atualizarValor=true e valor acima do saldo lança resgate_excede")
    void resgatarImportadoComAtualizarValorEValorExcedenteLancaErro() {
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));

        assertThatThrownBy(() -> investimentoService.resgatarImportado(
                10L, new BigDecimal("999999.00"), 5L, LocalDate.now(), "key-7", true, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("excede o saldo");

        verifyNoInteractions(transacaoService);
    }

    @Test
    @DisplayName("resgatarImportado com atualizarValor=false NÃO valida resgate_excede — registro histórico não deve ser bloqueado")
    void resgatarImportadoComAtualizarValorFalsoNaoValidaExcedente() {
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(), eq(1L), eq(true))).thenReturn(TRANSACAO_MOCK);

        investimentoService.resgatarImportado(10L, new BigDecimal("999999.00"), 5L, LocalDate.now(),
                "key-8", false, 1L);

        assertThat(investimentoPadrao.getValorAtual()).isEqualByComparingTo(new BigDecimal("1500.00"));
        verify(investimentoRepository, never()).save(any());
    }

    // ─── excluirMovimentacaoPorTransacao (desfazer de importação) ─────────────

    @Test
    @DisplayName("excluirMovimentacaoPorTransacao reverte um APORTE: desfaz efeito financeiro e subtrai do valorAtual")
    void excluirMovimentacaoPorTransacaoReverteAporte() {
        MovimentacaoInvestimentoEntity mov = new MovimentacaoInvestimentoEntity();
        mov.setInvestimentoId(10L);
        mov.setTipo(TipoMovimentacaoInvestimento.APORTE);
        mov.setValor(new BigDecimal("300.00"));
        mov.setTransacaoId(555L);

        TransacaoEntity transacao = new TransacaoEntity();
        transacao.setId(555L);
        transacao.setAtivo(true);

        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoRepository.findById(555L)).thenReturn(Optional.of(transacao));
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        investimentoService.excluirMovimentacaoPorTransacao(mov, 1L);

        assertThat(investimentoPadrao.getValorAtual()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(transacao.isAtivo()).isFalse();
        assertThat(mov.isAtivo()).isFalse();
        verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(transacao, 1L);
        verify(patrimonioHistoricoService).atualizarSnapshotUsuarioHoje(1L);
    }

    @Test
    @DisplayName("excluirMovimentacaoPorTransacao reverte um RESGATE: soma de volta ao valorAtual")
    void excluirMovimentacaoPorTransacaoReverteResgate() {
        MovimentacaoInvestimentoEntity mov = new MovimentacaoInvestimentoEntity();
        mov.setInvestimentoId(10L);
        mov.setTipo(TipoMovimentacaoInvestimento.RESGATE);
        mov.setValor(new BigDecimal("300.00"));
        mov.setTransacaoId(556L);

        TransacaoEntity transacao = new TransacaoEntity();
        transacao.setId(556L);
        transacao.setAtivo(true);

        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoRepository.findById(556L)).thenReturn(Optional.of(transacao));
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        investimentoService.excluirMovimentacaoPorTransacao(mov, 1L);

        assertThat(investimentoPadrao.getValorAtual()).isEqualByComparingTo(new BigDecimal("1800.00"));
        verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(transacao, 1L);
    }

    @Test
    @DisplayName("REGRESSÃO: excluir movimentação com ajustouValor=false NÃO altera o valorAtual — reverter um valor nunca somado corromperia o saldo")
    void excluirMovimentacaoSemAjusteNaoAlteraValorAtual() {
        MovimentacaoInvestimentoEntity mov = new MovimentacaoInvestimentoEntity();
        mov.setInvestimentoId(10L);
        mov.setTipo(TipoMovimentacaoInvestimento.APORTE);
        mov.setValor(new BigDecimal("300.00"));
        mov.setTransacaoId(558L);
        mov.setAjustouValor(false);

        TransacaoEntity transacao = new TransacaoEntity();
        transacao.setId(558L);
        transacao.setAtivo(true);

        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoRepository.findById(558L)).thenReturn(Optional.of(transacao));
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        investimentoService.excluirMovimentacaoPorTransacao(mov, 1L);

        assertThat(investimentoPadrao.getValorAtual()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(transacao.isAtivo()).isFalse();
        assertThat(mov.isAtivo()).isFalse();
        verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(transacao, 1L);
    }

    @Test
    @DisplayName("aportarImportado com atualizarValor=false grava a movimentação com ajustouValor=false")
    void aportarImportadoPersisteFlagAjustouValor() {
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(transacaoService.criarTransacaoInterna(any(), eq(1L), eq(true))).thenReturn(TRANSACAO_MOCK);

        investimentoService.aportarImportado(10L, new BigDecimal("200.00"), 5L, LocalDate.now(),
                "key-flag", false, 1L);

        ArgumentCaptor<MovimentacaoInvestimentoEntity> captor =
                ArgumentCaptor.forClass(MovimentacaoInvestimentoEntity.class);
        verify(movimentacaoInvestimentoRepository).save(captor.capture());
        assertThat(captor.getValue().isAjustouValor()).isFalse();
    }

    @Test
    @DisplayName("SEGURANÇA (IDOR): excluirMovimentacaoPorTransacao barra investimento de outro usuário")
    void excluirMovimentacaoPorTransacaoBarraOutroUsuario() {
        UsuarioEntity outroUsuario = new UsuarioEntity();
        outroUsuario.setId(2L);
        investimentoPadrao.setUsuario(outroUsuario);
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));

        MovimentacaoInvestimentoEntity mov = new MovimentacaoInvestimentoEntity();
        mov.setInvestimentoId(10L);
        mov.setTipo(TipoMovimentacaoInvestimento.APORTE);
        mov.setValor(new BigDecimal("300.00"));
        mov.setTransacaoId(557L);

        assertThatThrownBy(() -> investimentoService.excluirMovimentacaoPorTransacao(mov, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verifyNoInteractions(movimentacaoFinanceiraService);
    }

    @Test
    void excluirMovimentacoesEmMassaContaTodasComoExcluidasQuandoNenhumItemFalha() {
        var resultado = investimentoService.excluirMovimentacoesEmMassa(List.of(1L, 2L, 3L), 1L);

        assertThat(resultado.excluidas()).isEqualTo(3);
        assertThat(resultado.erros()).isEmpty();
        verify(self).excluirMovimentacao(1L, 1L);
        verify(self).excluirMovimentacao(2L, 1L);
        verify(self).excluirMovimentacao(3L, 1L);
    }

    @Test
    void excluirMovimentacoesEmMassaProcessaOQuePodeQuandoUmItemFalha() {
        lenient().doThrow(new RecursoNaoEncontradoException("Movimentação não encontrada"))
                .when(self).excluirMovimentacao(2L, 1L);

        var resultado = investimentoService.excluirMovimentacoesEmMassa(List.of(1L, 2L, 3L), 1L);

        assertThat(resultado.excluidas()).isEqualTo(2);
        assertThat(resultado.erros()).hasSize(1);
        assertThat(resultado.erros().get(0).itemId()).isEqualTo(2L);
        verify(self).excluirMovimentacao(1L, 1L);
        verify(self).excluirMovimentacao(3L, 1L);
    }

    @Test
    void excluirMovimentacoesEmMassaComListaVaziaNaoFazNada() {
        var resultado = investimentoService.excluirMovimentacoesEmMassa(List.of(), 1L);

        assertThat(resultado.excluidas()).isZero();
        assertThat(resultado.erros()).isEmpty();
        verifyNoInteractions(self);
    }
}
