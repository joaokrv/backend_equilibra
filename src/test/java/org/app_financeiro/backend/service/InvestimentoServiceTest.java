package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.dto.response.TransacaoResponseDTO;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoInvestimento;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
    private org.app_financeiro.backend.repository.TransacaoRepository transacaoRepository;

    @Mock
    private MovimentacaoFinanceiraService movimentacaoFinanceiraService;

    @InjectMocks
    private InvestimentoService investimentoService;

    private static final TransacaoResponseDTO TRANSACAO_MOCK = new TransacaoResponseDTO(
        99L, "Aporte", java.math.BigDecimal.TEN, java.time.LocalDate.now(),
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

}
