package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.ContaRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.ContaResponseDTO;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.exception.SaldoInsuficienteException;
import org.app_financeiro.backend.mapper.ContaMapper;
import org.app_financeiro.backend.repository.ContaRepository;
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
        // Arrange
        ContaRegistroRequestDTO request = new ContaRegistroRequestDTO("Nova Conta", new BigDecimal("500.00"));
        ContaResponseDTO responseEsperada = new ContaResponseDTO(10L, "Nova Conta", new BigDecimal("500.00"));

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(contaRepository.save(any(ContaEntity.class))).thenAnswer(invocation -> {
            ContaEntity c = invocation.getArgument(0);
            c.setId(10L);
            return c;
        });
        when(contaMapper.toResponse(any(ContaEntity.class))).thenReturn(responseEsperada);

        // Act
        ContaResponseDTO result = contaService.criarConta(request, 1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.nome()).isEqualTo("Nova Conta");
        assertThat(result.saldo()).isEqualTo(new BigDecimal("500.00"));
        verify(contaRepository).save(any(ContaEntity.class));
    }

    @Test
    void deveCriarContaComSaldoZeroQuandoNaoInformado() {
        // Arrange
        ContaRegistroRequestDTO request = new ContaRegistroRequestDTO("Conta Sem Saldo", null);
        ContaResponseDTO responseEsperada = new ContaResponseDTO(11L, "Conta Sem Saldo", BigDecimal.ZERO);

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(contaRepository.save(any(ContaEntity.class))).thenAnswer(invocation -> {
            ContaEntity c = invocation.getArgument(0);
            c.setId(11L);
            return c;
        });
        when(contaMapper.toResponse(any(ContaEntity.class))).thenReturn(responseEsperada);

        // Act
        ContaResponseDTO result = contaService.criarConta(request, 1L);

        // Assert
        assertThat(result.saldo()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void deveBuscarContaValidadaComSucesso() {
        // Arrange
        when(contaRepository.findById(10L)).thenReturn(Optional.of(contaPadrao));

        // Act
        ContaEntity result = contaService.buscarContaValidada(10L, 1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void deveLancarExcecaoQuandoBuscarContaDeOutroUsuario() {
        // Arrange
        when(contaRepository.findById(10L)).thenReturn(Optional.of(contaPadrao));

        // Act & Assert
        assertThatThrownBy(() -> contaService.buscarContaValidada(10L, 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("Conta não pertence ao usuário");
    }

    @Test
    void deveBuscarTodasContasDoUsuario() {
        // Arrange
        when(contaRepository.findByUsuarioId(1L)).thenReturn(List.of(contaPadrao));
        ContaResponseDTO responseDto = new ContaResponseDTO(10L, "Conta Corrente", new BigDecimal("100.00"));
        when(contaMapper.toResponse(contaPadrao)).thenReturn(responseDto);

        // Act
        List<ContaResponseDTO> result = contaService.buscarTodasDoUsuario(1L);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Conta Corrente");
    }

    @Test
    void deveDebitarSaldoComSucesso() {
        // Arrange
        BigDecimal valorSaque = new BigDecimal("40.00");
        when(contaRepository.findByIdWithLock(10L)).thenReturn(Optional.of(contaPadrao));
        when(contaRepository.save(any(ContaEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ContaEntity result = contaService.debitarSaldo(10L, valorSaque, 1L);

        // Assert
        assertThat(result.getSaldo()).isEqualByComparingTo(new BigDecimal("60.00"));
        verify(contaRepository).save(contaPadrao);
    }

    @Test
    void deveLancarSaldoInsuficienteExceptionAoTentarDebitarAlemDoLimite() {
        // Arrange
        BigDecimal valorSaque = new BigDecimal("150.00"); // Saldo é 100
        when(contaRepository.findByIdWithLock(10L)).thenReturn(Optional.of(contaPadrao));

        // Act & Assert
        assertThatThrownBy(() -> contaService.debitarSaldo(10L, valorSaque, 1L))
                .isInstanceOf(SaldoInsuficienteException.class)
                .hasMessageContaining("Saldo insuficiente");

        verify(contaRepository, never()).save(any());
    }

    @Test
    void deveCreditarSaldoComSucesso() {
        // Arrange
        BigDecimal deposito = new BigDecimal("50.00");
        when(contaRepository.findByIdWithLock(10L)).thenReturn(Optional.of(contaPadrao));
        when(contaRepository.save(any(ContaEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ContaEntity result = contaService.creditarSaldo(10L, deposito, 1L);

        // Assert
        assertThat(result.getSaldo()).isEqualByComparingTo(new BigDecimal("150.00"));
        verify(contaRepository).save(contaPadrao);
    }

    @Test
    void deveInativarContaComSucessoQuandoSaldoZero() {
        // Arrange
        contaPadrao.setSaldo(BigDecimal.ZERO);
        when(contaRepository.findById(10L)).thenReturn(Optional.of(contaPadrao));
        when(contaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        contaService.deletarConta(10L, 1L);

        // Assert
        assertThat(contaPadrao.isAtivo()).isFalse();
        verify(contaRepository).save(contaPadrao);
    }

    @Test
    void deveBloquearSoftDeleteDeContaComSaldoPositivo() {
        // Arrange
        // contaPadrao já tem saldo 100.00 no setUp()
        when(contaRepository.findById(10L)).thenReturn(Optional.of(contaPadrao));

        // Act & Assert
        assertThatThrownBy(() -> contaService.deletarConta(10L, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Não é possível inativar uma conta que ainda possui saldo");

        verify(contaRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoAtualizarSaldoComValorNegativo() {
        // Arrange
        when(contaRepository.findByIdWithLock(10L)).thenReturn(Optional.of(contaPadrao));

        // Act & Assert
        assertThatThrownBy(() -> contaService.atualizarSaldo(10L, new BigDecimal("-50.00"), 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("O saldo não pode ser negativo");

        verify(contaRepository, never()).save(any());
    }
}
