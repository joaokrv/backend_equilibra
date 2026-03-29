package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.projections.DividaCartaoProjection;
import org.app_financeiro.backend.dto.request.CartaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.CartaoResponseDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.enums.BandeiraCartao;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.exception.LimiteInsuficienteException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.mapper.CartaoMapper;
import org.app_financeiro.backend.repository.CartaoRepository;
import org.app_financeiro.backend.repository.FaturaRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartaoServiceTest {

    @Mock
    private CartaoRepository cartaoRepository;

    @Mock
    private FaturaRepository faturaRepository;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private CartaoMapper cartaoMapper;

    @InjectMocks
    private CartaoService cartaoService;

    private UsuarioEntity usuarioPadrao;
    private CartaoEntity cartaoPadrao;

    @BeforeEach
    void setUp() {
        usuarioPadrao = new UsuarioEntity();
        usuarioPadrao.setId(1L);
        usuarioPadrao.setNome("Joao");

        cartaoPadrao = new CartaoEntity();
        cartaoPadrao.setId(10L);
        cartaoPadrao.setNome("Nubank");
        cartaoPadrao.setLimite(new BigDecimal("1000.00"));
        cartaoPadrao.setDiaFechamento(3);
        cartaoPadrao.setDiaVencimento(10);
        cartaoPadrao.setUsuario(usuarioPadrao);
        cartaoPadrao.setBandeira(BandeiraCartao.VISA);
        cartaoPadrao.setAtivo(true);
    }

    @Test
    void deveCriarCartaoComSucessoELimiteDisponivelTotal() {
        // Arrange
        CartaoRegistroRequestDTO request = new CartaoRegistroRequestDTO("Nubank", new BigDecimal("1000.00"), 3, 10, BandeiraCartao.NUBANK);
        CartaoResponseDTO responseEsperada = new CartaoResponseDTO(10L, "Nubank", new BigDecimal("1000.00"), new BigDecimal("1000.00"), 3, 10, BandeiraCartao.NUBANK);

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(cartaoRepository.save(any(CartaoEntity.class))).thenAnswer(i -> {
            CartaoEntity c = i.getArgument(0);
            c.setId(10L);
            return c;
        });

        // Na criação, espera-se que o limite disponível seja igual ao limite total (pois não há faturas)
        when(cartaoMapper.toResponse(any(CartaoEntity.class), eq(new BigDecimal("1000.00"))))
                .thenReturn(responseEsperada);

        // Act
        CartaoResponseDTO result = cartaoService.criarCartao(request, 1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.limite()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(result.limiteDisponivel()).isEqualTo(new BigDecimal("1000.00"));

        verify(cartaoRepository).save(any(CartaoEntity.class));
    }

    @Test
    void deveBucarPorIdECalcularLimiteDisponivelCorretamenteCenarioZeroDividas() {
        // Arrange
        when(cartaoRepository.findById(10L)).thenReturn(Optional.of(cartaoPadrao));
        when(faturaRepository.findByCartaoIdAndStatusNot(10L, StatusFatura.PAGA)).thenReturn(List.of());

        CartaoResponseDTO responseEsperada = new CartaoResponseDTO(10L, "Nubank", new BigDecimal("1000.00"), new BigDecimal("1000.00"), 3, 10, BandeiraCartao.VISA);
        when(cartaoMapper.toResponse(cartaoPadrao, new BigDecimal("1000.00"))).thenReturn(responseEsperada);

        // Act
        CartaoResponseDTO result = cartaoService.buscarPorId(10L, 1L);

        // Assert
        assertThat(result.limiteDisponivel()).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void deveBucarPorIdECalcularLimiteDisponivelDiminuidoVariosMeses() {
        // Arrange
        FaturaEntity faturaJan = new FaturaEntity();
        faturaJan.setValorTotal(new BigDecimal("500.00"));
        faturaJan.setValorPago(new BigDecimal("200.00")); // Resta 300 de dívida

        FaturaEntity faturaFev = new FaturaEntity();
        faturaFev.setValorTotal(new BigDecimal("400.00"));
        faturaFev.setValorPago(BigDecimal.ZERO); // Resta 400 de dívida

        when(cartaoRepository.findById(10L)).thenReturn(Optional.of(cartaoPadrao));
        when(faturaRepository.findByCartaoIdAndStatusNot(10L, StatusFatura.PAGA))
                .thenReturn(List.of(faturaJan, faturaFev)); // Soma = 700 de dívida atual. 1000 - 700 = 300

        CartaoResponseDTO responseEsperada = new CartaoResponseDTO(10L, "Nubank", new BigDecimal("1000.00"), new BigDecimal("300.00"), 3, 10, BandeiraCartao.VISA);
        when(cartaoMapper.toResponse(cartaoPadrao, new BigDecimal("300.00"))).thenReturn(responseEsperada);

        // Act
        CartaoResponseDTO result = cartaoService.buscarPorId(10L, 1L);

        // Assert
        assertThat(result.limiteDisponivel()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void deveRetornarTodosCartoesDoUsuarioUsandoProjectionsParaEvitarNPlus1MuitasFaturas() {
        // Arrange
        CartaoEntity cartao2 = new CartaoEntity();
        cartao2.setId(20L);
        cartao2.setLimite(new BigDecimal("500.00"));

        when(cartaoRepository.findByUsuarioId(1L)).thenReturn(List.of(cartaoPadrao, cartao2));

        // Mock projections interface
        DividaCartaoProjection div1 = new DividaCartaoProjection(10L, new BigDecimal("600.00"));
        DividaCartaoProjection div2 = new DividaCartaoProjection(20L, BigDecimal.ZERO);

        when(faturaRepository.somarDividasPorCartoes(1L, StatusFatura.PAGA)).thenReturn(List.of(div1, div2));

        CartaoResponseDTO resp1 = new CartaoResponseDTO(10L, "Nubank", new BigDecimal("1000.00"), new BigDecimal("400.00"), 3, 10, BandeiraCartao.VISA);
        CartaoResponseDTO resp2 = new CartaoResponseDTO(20L, "CartaoNovo", new BigDecimal("500.00"), new BigDecimal("500.00"), 3, 10, BandeiraCartao.MASTERCARD);

        when(cartaoMapper.toResponse(cartaoPadrao, new BigDecimal("400.00"))).thenReturn(resp1);
        when(cartaoMapper.toResponse(cartao2, new BigDecimal("500.00"))).thenReturn(resp2);

        // Act
        List<CartaoResponseDTO> result = cartaoService.buscarTodosDoUsuario(1L);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).limiteDisponivel()).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(result.get(1).limiteDisponivel()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void deveConsumirLimiteDoCartaoQuandoTemSaldoSuficiente() {
        // Arrange
        when(cartaoRepository.findById(10L)).thenReturn(Optional.of(cartaoPadrao));
        when(faturaRepository.findByCartaoIdAndStatusNot(10L, StatusFatura.PAGA)).thenReturn(List.of());

        // Lembre-se que o consumirLimite() não faz save() dele, retorna a entity e apenas previne o gasto

        // Act
        CartaoEntity result = cartaoService.consumirLimite(10L, new BigDecimal("250.00"), 1L);

        // Assert
        assertThat(result).isNotNull();
        // Nenhuma exception lançada
    }


    @Test
    void deveLancarLimiteInsuficienteQuandoFaturasComemTodoLimite() {
        // Arrange
        FaturaEntity fatura = new FaturaEntity();
        fatura.setValorTotal(new BigDecimal("950.00"));
        fatura.setValorPago(BigDecimal.ZERO);

        when(cartaoRepository.findById(10L)).thenReturn(Optional.of(cartaoPadrao));
        when(faturaRepository.findByCartaoIdAndStatusNot(10L, StatusFatura.PAGA)).thenReturn(List.of(fatura)); // Sobrou 50 conto

        // Act & Assert
        assertThatThrownBy(() -> cartaoService.consumirLimite(10L, new BigDecimal("51.00"), 1L))
                .isInstanceOf(LimiteInsuficienteException.class)
                .hasMessageContaining("Limite insuficiente no cartão");
    }

    @Test
    void deveDeletarCartaoSemDividasPendentes() {
        // Arrange
        when(cartaoRepository.findById(10L)).thenReturn(Optional.of(cartaoPadrao));
        when(faturaRepository.existsByCartaoIdAndStatusNot(10L, StatusFatura.PAGA)).thenReturn(false);
        when(cartaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        cartaoService.deletarCartao(10L, 1L);

        // Assert
        assertThat(cartaoPadrao.isAtivo()).isFalse();
        verify(cartaoRepository).save(cartaoPadrao);
    }

    @Test
    void deveBarraoSoftDeleteSeTiverFaturaAbertaOuAtrasada() {
        // Arrange
        when(cartaoRepository.findById(10L)).thenReturn(Optional.of(cartaoPadrao));
        when(faturaRepository.existsByCartaoIdAndStatusNot(10L, StatusFatura.PAGA)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> cartaoService.deletarCartao(10L, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("Não é possível deletar um cartão que possui faturas pendentes");

        verify(cartaoRepository, never()).save(any());
    }

    @Test
    void deveLancarExcecaoAoBuscarDeOutroUsuario() {
        // Arrange
        when(cartaoRepository.findById(10L)).thenReturn(Optional.of(cartaoPadrao));

        // Act & Assert
        assertThatThrownBy(() -> cartaoService.buscarCartaoValidado(10L, 99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("pertence ao usuário");
    }
}
