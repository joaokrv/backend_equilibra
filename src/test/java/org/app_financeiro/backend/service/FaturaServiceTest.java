package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.request.PagarFaturaRequestDTO;
import org.app_financeiro.backend.dto.response.FaturaResponseDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.mapper.FaturaMapper;
import org.app_financeiro.backend.repository.FaturaRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaturaServiceTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private CartaoService cartaoService;

    @Mock
    private ContaService contaService;

    @Mock
    private FaturaRepository faturaRepository;

    @Mock
    private FaturaMapper faturaMapper;

    @InjectMocks
    private FaturaService faturaService;

    private UsuarioEntity usuarioPadrao;
    private CartaoEntity cartaoPadrao;
    private FaturaEntity faturaAberta;

    @BeforeEach
    void setUp() {
        usuarioPadrao = new UsuarioEntity();
        usuarioPadrao.setId(1L);
        usuarioPadrao.setNome("Joao");

        cartaoPadrao = new CartaoEntity();
        cartaoPadrao.setId(10L);
        cartaoPadrao.setNome("Nubank");
        cartaoPadrao.setDiaFechamento(5);
        cartaoPadrao.setDiaVencimento(10);
        cartaoPadrao.setUsuario(usuarioPadrao);

        faturaAberta = new FaturaEntity();
        faturaAberta.setId(100L);
        faturaAberta.setCartao(cartaoPadrao);
        faturaAberta.setUsuario(usuarioPadrao);
        faturaAberta.setMes(10);
        faturaAberta.setAno(2023);
        faturaAberta.setValorTotal(new BigDecimal("500.00"));
        faturaAberta.setValorPago(new BigDecimal("100.00"));
        faturaAberta.setStatus(StatusFatura.ABERTA);
        faturaAberta.setDataFechamento(LocalDate.of(2023, 10, 5));
        faturaAberta.setDataVencimento(LocalDate.of(2023, 10, 10));
    }

    @Test
    void deveCriarNovaFaturaQuandoNaoHouverFaturaAbertaParaOmesmoMes() {
        // Arrange (Transacao dia 2 de Outubro - Fatura fecha dia 5 - Cai na fatura de Outubro mesmo)
        LocalDate dataTransacao = LocalDate.of(2023, 10, 2);
        BigDecimal valorAcrescentar = new BigDecimal("150.00");

        when(faturaRepository.findByCartaoIdAndMesAndAno(10L, 10, 2023)).thenReturn(Optional.empty()); // Lazy Creation
        when(faturaRepository.save(any(FaturaEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        FaturaEntity result = faturaService.adicionarTransacao(cartaoPadrao, dataTransacao, valorAcrescentar);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getValorTotal()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.getMes()).isEqualTo(10);
        assertThat(result.getAno()).isEqualTo(2023);
        assertThat(result.getStatus()).isEqualTo(StatusFatura.ABERTA);

        verify(faturaRepository).save(any(FaturaEntity.class));
    }

    @Test
    void deveAcrescentarAoValorDeUmaFaturaExistente() {
        // Arrange (Transacao dia 3 de Outubro - fatura de Out já existe)
        LocalDate dataTransacao = LocalDate.of(2023, 10, 3);
        BigDecimal valorDaKopenhagen = new BigDecimal("50.00");

        when(faturaRepository.findByCartaoIdAndMesAndAno(10L, 10, 2023)).thenReturn(Optional.of(faturaAberta));
        when(faturaRepository.save(any(FaturaEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        FaturaEntity result = faturaService.adicionarTransacao(cartaoPadrao, dataTransacao, valorDaKopenhagen);

        // Assert
        assertThat(result.getValorTotal()).isEqualByComparingTo(new BigDecimal("550.00")); // Era 500
        verify(faturaRepository).save(faturaAberta);
    }

    @Test
    void deveEmpurrarParaFaturaDoProximoMesSeTransacaoAcabarPassandoDaDataDeFechamento() {
        // Arrange (Transação dia 7 - Cartão fecha dia 5 - Logo, cai na fatura de Novembro)
        LocalDate dataTransacaoAtrasada = LocalDate.of(2023, 10, 7);
        BigDecimal ps5 = new BigDecimal("4500.00");

        when(faturaRepository.findByCartaoIdAndMesAndAno(10L, 11, 2023)).thenReturn(Optional.empty()); // Não tem em Novembro ainda
        when(faturaRepository.save(any(FaturaEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        FaturaEntity result = faturaService.adicionarTransacao(cartaoPadrao, dataTransacaoAtrasada, ps5);

        // Assert
        assertThat(result.getMes()).isEqualTo(11); // Foi pra Novembro
        assertThat(result.getAno()).isEqualTo(2023);
        assertThat(result.getValorTotal()).isEqualByComparingTo(new BigDecimal("4500.00"));
    }

    @Test
    void deveRemoverValorDaFaturaComLimiteZeroParaEvitarNegativoNumEstorno() {
        // Arrange
        BigDecimal ps5Cancelado = new BigDecimal("4500.00"); // Estorno maior que a dívida de 500

        // Act
        faturaService.removerTransacaoPorFatura(faturaAberta, ps5Cancelado);

        // Assert
        assertThat(faturaAberta.getValorTotal()).isEqualByComparingTo(BigDecimal.ZERO); // Não ficamos negativos
        verify(faturaRepository).save(faturaAberta);
    }

    @Test
    void devePagarIntegralFaturaESetarComoPaga() {
        // Arrange (A dívida restante é 500 total - 100 pago = 400 devendo)
        PagarFaturaRequestDTO dtoPagamento = new PagarFaturaRequestDTO(5L, new BigDecimal("400.00"));

        // Mock Customizado
        when(faturaRepository.findById(100L)).thenReturn(Optional.of(faturaAberta));
        // contaService.debitarSaldo fará o débito (Void returns em Mocks não precisam de mock, exceção não é lançada se sucesso)
        when(faturaRepository.save(any(FaturaEntity.class))).thenAnswer(i -> i.getArgument(0));

        FaturaResponseDTO responseDTO = new FaturaResponseDTO(
                100L, 10L, "Nubank", 10, 2023, 
                new BigDecimal("500.00"), new BigDecimal("500.00"), BigDecimal.ZERO,
                StatusFatura.PAGA, LocalDate.of(2023, 10, 10), LocalDate.of(2023, 10, 5));

        when(faturaMapper.toResponse(any())).thenReturn(responseDTO);

        // Act
        FaturaResponseDTO result = faturaService.pagarFatura(100L, 1L, dtoPagamento);

        // Assert
        assertThat(faturaAberta.getStatus()).isEqualTo(StatusFatura.PAGA);
        assertThat(faturaAberta.getValorPago()).isEqualByComparingTo(new BigDecimal("500.00"));

        verify(contaService).debitarSaldo(5L, new BigDecimal("400.00"), 1L); // Foi cobrar a conta
        verify(faturaRepository).save(faturaAberta);
    }

    @Test
    void deveLancarExceptionAoPagarAMaisDoQueADividaRestante() {
        // Arrange
        // Dívida é 400 (500 total - 100 pago). Cara manda 401.00 de pagamento.
        PagarFaturaRequestDTO pagamentoOversized = new PagarFaturaRequestDTO(5L, new BigDecimal("401.00"));
        when(faturaRepository.findById(100L)).thenReturn(Optional.of(faturaAberta));

        // Act & Assert
        assertThatThrownBy(() -> faturaService.pagarFatura(100L, 1L, pagamentoOversized))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("não pode ser maior que o restante da fatura");

        verify(contaService, never()).debitarSaldo(any(), any(), any());
    }

    @Test
    void deveLancarExceptionSeTentarPagarFaturaJaPaga() {
        // Arrange
        faturaAberta.setStatus(StatusFatura.PAGA);
        PagarFaturaRequestDTO pagamento = new PagarFaturaRequestDTO(5L, new BigDecimal("50.00"));
        when(faturaRepository.findById(100L)).thenReturn(Optional.of(faturaAberta));

        // Act & Assert
        assertThatThrownBy(() -> faturaService.pagarFatura(100L, 1L, pagamento))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("já está totalmente paga");
    }

    @Test
    void fechamentoFantasmaListarFaturasPorCartaoTransformaAbertaEmFechadaEFechadaEmAtrasada() {
        // Arrange
        // (Hoje é, ex: 15 de Outubro). Testaremos com LocalDate.now() na logica (o que atrapalha Mocks,
        // mas as datas estao mto antigas 2023, entao elas com certeza serao validadas para Fechada/Atrasada
        // Ja que Vencimento é 10/10/2023 e Hoje é >2024.

        when(faturaRepository.findByCartaoId(10L)).thenReturn(List.of(faturaAberta));

        // Act
        List<FaturaResponseDTO> result = faturaService.listarFaturasPorCartao(10L, 1L);

        // Assert
        ArgumentCaptor<List<FaturaEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(faturaRepository).saveAll(captor.capture());

        List<FaturaEntity> salvas = captor.getValue();
        // CUIDADO: O codigo real roda "hoje > dataFechamento" (sim, 2024 > 2023) -> cai pra ABERTA->FECHADA
        // Depois a mesma iteração avalia: se agora ela é FECHADA e "hoje > dataVencimento", cai pra ATRASADA.
        // Assim um "salto no tempo" conserta os dois. Fantástico.
        assertThat(salvas.get(0).getStatus()).isEqualTo(StatusFatura.ATRASADA);
    }
}
