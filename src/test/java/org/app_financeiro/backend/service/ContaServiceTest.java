package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.ContaResponseDTO;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.exception.SaldoInsuficienteException;
import org.app_financeiro.backend.mapper.ContaMapper;
import org.app_financeiro.backend.repository.CartaoRepository;
import org.app_financeiro.backend.repository.ContaRepository;
import org.app_financeiro.backend.repository.InvestimentoRepository;
import org.app_financeiro.backend.repository.TransacaoRecorrenteRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
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
class ContaServiceTest {

    @Mock
    private ContaRepository contaRepository;

    @Mock
    private InvestimentoRepository investimentoRepository;

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private TransacaoRecorrenteRepository transacaoRecorrenteRepository;

    @Mock
    private CartaoRepository cartaoRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private ContaMapper contaMapper;

    @InjectMocks
    private ContaService contaService;

    private UsuarioEntity usuarioPadrao;
    private ContaEntity contaPadrao;

    @BeforeEach
    void setUp() {
        usuarioPadrao = new UsuarioEntity();
        usuarioPadrao.setId(1L);
        usuarioPadrao.setNome("Joao");

        contaPadrao = new ContaEntity();
        contaPadrao.setId(10L);
        contaPadrao.setNome("Conta Corrente");
        contaPadrao.setSaldo(new BigDecimal("100.00"));
        contaPadrao.setUsuario(usuarioPadrao);
        contaPadrao.setAtivo(true);
    }

    @Test
    void deveCriarContaComSucesso() {
        ContaRegistroRequestDTO request = new ContaRegistroRequestDTO("Nova Conta", new BigDecimal("500.00"));
        ContaResponseDTO responseEsperada = new ContaResponseDTO(10L, "Nova Conta", new BigDecimal("500.00"));

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(contaRepository.save(any(ContaEntity.class))).thenAnswer(invocation -> {
            ContaEntity c = invocation.getArgument(0);
            c.setId(10L);
            return c;
        });
        when(contaMapper.toResponse(any(ContaEntity.class))).thenReturn(responseEsperada);

        ContaResponseDTO result = contaService.criarConta(request, 1L);

        assertThat(result).isNotNull();
        assertThat(result.nome()).isEqualTo("Nova Conta");
        assertThat(result.saldo()).isEqualTo(new BigDecimal("500.00"));
        verify(contaRepository).save(any(ContaEntity.class));
    }

    @Test
    void deveCriarContaComSaldoZeroQuandoNaoInformado() {
        ContaRegistroRequestDTO request = new ContaRegistroRequestDTO("Conta Sem Saldo", null);
        ContaResponseDTO responseEsperada = new ContaResponseDTO(11L, "Conta Sem Saldo", BigDecimal.ZERO);

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(contaRepository.save(any(ContaEntity.class))).thenAnswer(invocation -> {
            ContaEntity c = invocation.getArgument(0);
            c.setId(11L);
            return c;
        });
        when(contaMapper.toResponse(any(ContaEntity.class))).thenReturn(responseEsperada);

        ContaResponseDTO result = contaService.criarConta(request, 1L);

        assertThat(result.saldo()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void deveBuscarContaValidadaComSucesso() {
        when(contaRepository.findById(10L)).thenReturn(Optional.of(contaPadrao));

        ContaEntity result = contaService.buscarContaValidada(10L, 1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void deveLancarExcecaoQuandoBuscarContaDeOutroUsuario() {
        when(contaRepository.findById(10L)).thenReturn(Optional.of(contaPadrao));

        assertThatThrownBy(() -> contaService.buscarContaValidada(10L, 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Conta não pertence ao usuário");
    }

    @Test
    void deveBuscarTodasContasDoUsuario() {
        when(contaRepository.findByUsuarioId(1L)).thenReturn(List.of(contaPadrao));
        ContaResponseDTO responseDto = new ContaResponseDTO(10L, "Conta Corrente", new BigDecimal("100.00"));
        when(contaMapper.toResponse(contaPadrao)).thenReturn(responseDto);

        List<ContaResponseDTO> result = contaService.buscarTodasDoUsuario(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Conta Corrente");
    }

    @Test
    void deveDebitarSaldoComSucesso() {
        BigDecimal valorSaque = new BigDecimal("40.00");
        when(contaRepository.findByIdWithLock(10L)).thenReturn(Optional.of(contaPadrao));
        when(contaRepository.save(any(ContaEntity.class))).thenAnswer(i -> i.getArgument(0));

        ContaEntity result = contaService.debitarSaldo(10L, valorSaque, 1L);

        assertThat(result.getSaldo()).isEqualByComparingTo(new BigDecimal("60.00"));
        verify(contaRepository).save(contaPadrao);
    }

    @Test
    void deveLancarSaldoInsuficienteExceptionAoTentarDebitarAlemDoLimite() {
        BigDecimal valorSaque = new BigDecimal("150.00");
        when(contaRepository.findByIdWithLock(10L)).thenReturn(Optional.of(contaPadrao));

        assertThatThrownBy(() -> contaService.debitarSaldo(10L, valorSaque, 1L))
                .isInstanceOf(SaldoInsuficienteException.class)
                .hasMessageContaining("Saldo insuficiente");

        verify(contaRepository, never()).save(any());
    }

    @Test
    void deveCreditarSaldoComSucesso() {
        BigDecimal deposito = new BigDecimal("50.00");
        when(contaRepository.findByIdWithLock(10L)).thenReturn(Optional.of(contaPadrao));
        when(contaRepository.save(any(ContaEntity.class))).thenAnswer(i -> i.getArgument(0));

        ContaEntity result = contaService.creditarSaldo(10L, deposito, 1L);

        assertThat(result.getSaldo()).isEqualByComparingTo(new BigDecimal("150.00"));
        verify(contaRepository).save(contaPadrao);
    }

    @Test
    void deveInativarContaComSucessoQuandoSaldoZero() {
        contaPadrao.setSaldo(BigDecimal.ZERO);
        when(contaRepository.findById(10L)).thenReturn(Optional.of(contaPadrao));
        when(contaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(investimentoRepository.inativarVinculadosAConta(1L, 10L)).thenReturn(1);
        when(transacaoRepository.inativarPorConta(1L, 10L)).thenReturn(2);
        when(transacaoRecorrenteRepository.inativarPorConta(1L, 10L)).thenReturn(1);
        when(cartaoRepository.desvincularConta(1L, 10L)).thenReturn(1);

        contaService.deletarConta(10L, 1L);

        assertThat(contaPadrao.isAtivo()).isFalse();
        verify(contaRepository).save(contaPadrao);
        verify(investimentoRepository).inativarVinculadosAConta(1L, 10L);
        verify(transacaoRepository).inativarPorConta(1L, 10L);
        verify(transacaoRecorrenteRepository).inativarPorConta(1L, 10L);
        verify(cartaoRepository).desvincularConta(1L, 10L);
    }

    @Test
    void deveBloquearSoftDeleteDeContaComSaldoPositivo() {
        when(contaRepository.findById(10L)).thenReturn(Optional.of(contaPadrao));

        assertThatThrownBy(() -> contaService.deletarConta(10L, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Não é possível inativar uma conta que ainda possui saldo");

        verify(contaRepository, never()).save(any());
        verify(investimentoRepository, never()).inativarVinculadosAConta(anyLong(), anyLong());
        verify(transacaoRepository, never()).inativarPorConta(anyLong(), anyLong());
        verify(transacaoRecorrenteRepository, never()).inativarPorConta(anyLong(), anyLong());
        verify(cartaoRepository, never()).desvincularConta(anyLong(), anyLong());
    }

    @Test
    void deveLancarExcecaoAoAtualizarSaldoComValorNegativo() {
        when(contaRepository.findByIdWithLock(10L)).thenReturn(Optional.of(contaPadrao));

        assertThatThrownBy(() -> contaService.atualizarSaldo(10L, new BigDecimal("-50.00"), 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("O saldo não pode ser negativo");

        verify(contaRepository, never()).save(any());
    }
}
