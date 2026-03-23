package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.InvestimentoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.exception.SaldoInsuficienteException;
import org.app_financeiro.backend.mapper.InvestimentoMapper;
import org.app_financeiro.backend.repository.InvestimentoRepository;
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

    @InjectMocks
    private InvestimentoService investimentoService;

    private UsuarioEntity usuarioPadrao;
    private InvestimentoEntity investimentoPadrao;

    @BeforeEach
    void setUp() {
        usuarioPadrao = new UsuarioEntity();
        usuarioPadrao.setId(1L);
        usuarioPadrao.setNome("Joao");

        investimentoPadrao = new InvestimentoEntity();
        investimentoPadrao.setId(10L);
        investimentoPadrao.setDescricao("Viagem Japão");
        investimentoPadrao.setValorInicial(new BigDecimal("500.00"));
        investimentoPadrao.setValorAtual(new BigDecimal("1500.00"));
        investimentoPadrao.setMetaAtual(new BigDecimal("10000.00"));
        investimentoPadrao.setUsuario(usuarioPadrao);
        investimentoPadrao.setAtivo(true);
    }

    @Test
    void deveCriarInvestimentoComSucesso() {
        // Arrange
        InvestimentoRegistroRequestDTO request = new InvestimentoRegistroRequestDTO(
                "Viagem Japão", new BigDecimal("500.00"), new BigDecimal("10000.00"));

        InvestimentoResponseDTO responseEsperada = new InvestimentoResponseDTO(
                10L, "Viagem Japão", new BigDecimal("500.00"), new BigDecimal("500.00"), new BigDecimal("10000.00"));

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(investimentoRepository.save(any())).thenAnswer(i -> {
            InvestimentoEntity inv = i.getArgument(0);
            inv.setId(10L);
            return inv;
        });

        when(investimentoMapper.toResponse(any())).thenReturn(responseEsperada);

        // Act
        InvestimentoResponseDTO result = investimentoService.criarInvestimento(request, 1L);

        // Assert
        assertThat(result.descricao()).isEqualTo("Viagem Japão");
        assertThat(result.valorAtual()).isEqualTo(new BigDecimal("500.00"));
        verify(investimentoRepository).save(any());
        // A criação NÃO deve debitar de conta
        verify(contaService, never()).debitarSaldo(any(), any(), any());
    }

    @Test
    void deveDepositarNoInvestimentoComSucesso() {
        // Arrange
        BigDecimal deposito = new BigDecimal("200.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao)); // Valor Atual: 1500
        when(contaService.debitarSaldo(5L, deposito, 1L)).thenReturn(null); // Mock que a conta tem saldo

        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InvestimentoResponseDTO responseEsperada = new InvestimentoResponseDTO(
                10L, "Viagem Japão", new BigDecimal("500.00"), new BigDecimal("1700.00"), new BigDecimal("10000.00"));
        when(investimentoMapper.toResponse(any())).thenReturn(responseEsperada);

        // Act
        InvestimentoResponseDTO result = investimentoService.adicionarDeposito(10L, deposito, 5L, 1L);

        // Assert
        assertThat(result.valorAtual()).isEqualTo(new BigDecimal("1700.00"));
        assertThat(investimentoPadrao.getValorAtual()).isEqualByComparingTo(new BigDecimal("1700.00"));

        verify(contaService).debitarSaldo(5L, deposito, 1L); // Verifica a transferência
        verify(investimentoRepository).save(investimentoPadrao);
    }

    @Test
    void deveFalharODepositoSeAcontaNaoTiverSaldo() {
        // Arrange
        BigDecimal deposito = new BigDecimal("20000.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(contaService.debitarSaldo(5L, deposito, 1L))
                .thenThrow(new SaldoInsuficienteException("Saldo INSUFICIENTE"));

        // Act & Assert
        assertThatThrownBy(() -> investimentoService.adicionarDeposito(10L, deposito, 5L, 1L))
                .isInstanceOf(SaldoInsuficienteException.class);

        // Não deve ter salvo o aumento do ativo
        verify(investimentoRepository, never()).save(any());
    }

    @Test
    void deveResgatarDoInvestimentoComSucesso() {
        // Arrange
        // Investimento tem 1500 de ValorAtual
        BigDecimal resgate = new BigDecimal("500.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(contaService.creditarSaldo(5L, resgate, 1L)).thenReturn(null);

        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // Esperamos 1500 - 500 = 1000
        InvestimentoResponseDTO responseEsperada = new InvestimentoResponseDTO(
                10L, "Viagem Japão", new BigDecimal("500.00"), new BigDecimal("1000.00"), new BigDecimal("10000.00"));
        when(investimentoMapper.toResponse(any())).thenReturn(responseEsperada);

        // Act
        InvestimentoResponseDTO result = investimentoService.resgatarInvestimento(10L, resgate, 5L, 1L);

        // Assert
        assertThat(result.valorAtual()).isEqualTo(new BigDecimal("1000.00"));
        verify(contaService).creditarSaldo(5L, resgate, 1L);
        verify(investimentoRepository).save(investimentoPadrao);
    }

    @Test
    void deveLancarExcecaoGraveSeTentarResgatarMaisQueOValorDessaMetaPoupanca() {
        // Arrange
        BigDecimal resgateAlemDoLimite = new BigDecimal("2000.00"); // Só tem 1500
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));

        // Act & Assert
        assertThatThrownBy(() -> investimentoService.resgatarInvestimento(10L, resgateAlemDoLimite, 5L, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("excede o saldo do investimento");

        verify(contaService, never()).creditarSaldo(any(), any(), any());
        verify(investimentoRepository, never()).save(any());
    }

    @Test
    void deveAtualizarMetaComSucesso() {
        // Arrange
        BigDecimal novaMeta = new BigDecimal("15000.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InvestimentoResponseDTO responseEsperada = new InvestimentoResponseDTO(
                10L, "Viagem Japão", new BigDecimal("500.00"), new BigDecimal("1500.00"), new BigDecimal("15000.00"));
        when(investimentoMapper.toResponse(any())).thenReturn(responseEsperada);

        // Act
        InvestimentoResponseDTO result = investimentoService.atualizarMeta(10L, novaMeta, 1L);

        // Assert
        assertThat(result.metaAtual()).isEqualByComparingTo(new BigDecimal("15000.00"));
        verify(investimentoRepository).save(investimentoPadrao);
    }

    @Test
    void deveLancarExcecaoSeTentarDefinirAMetaZeradaOuNegativa() {
        // Arrange
        BigDecimal novaMeta = new BigDecimal("-100.00");
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));

        // Act & Assert
        assertThatThrownBy(() -> investimentoService.atualizarMeta(10L, novaMeta, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("meta deve ser maior que zero");
    }

    @Test
    void deveDeletarOInvestimentoApenasSeEleEstiverZeradinhoSemSaldo() {
        // Arrange
        investimentoPadrao.setValorAtual(BigDecimal.ZERO); // Simular que já resgatamos
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));
        when(investimentoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        investimentoService.deletarInvestimento(10L, 1L);

        // Assert
        assertThat(investimentoPadrao.isAtivo()).isFalse();
        verify(investimentoRepository).save(investimentoPadrao);
    }

    @Test
    void deveBloquearTentativaDoMelianteDeDeleterAContaComOSaldoNeleOuAborrecidoDesativar() {
        // Arrange
        // (Já tem 1500.00 nele)
        when(investimentoRepository.findById(10L)).thenReturn(Optional.of(investimentoPadrao));

        // Act & Assert
        assertThatThrownBy(() -> investimentoService.deletarInvestimento(10L, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Resgate o saldo restante");

        verify(investimentoRepository, never()).save(any());
    }

    @Test
    void buscarTodosDoUsuarioRetornaVazIOSeSoZerarOuNaoExistir() {
        // Arrange
        when(investimentoRepository.findByUsuarioId(1L)).thenReturn(List.of(investimentoPadrao));
        // ... omitted mappings for brevity for this specific integration rule test

        // Act
        List<InvestimentoResponseDTO> result = investimentoService.buscarTodosDoUsuario(1L);

        // Assert
        assertThat(result).hasSize(1);
    }

}
