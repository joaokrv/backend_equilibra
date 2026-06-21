package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.model.ResultadoMovimentacaoCartao;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.dto.response.TransacaoResponseDTO;
import org.app_financeiro.backend.entity.*;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusFatura;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.exception.OperacaoNaoPermitidaException;
import org.app_financeiro.backend.exception.RecursoNaoEncontradoException;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.app_financeiro.backend.mapper.TransacaoMapper;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.repository.TransacaoRecorrenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransacaoServiceTest {

    @Mock
    private TransacaoRepository transacaoRepository;

    @Mock
    private MovimentacaoFinanceiraService movimentacaoFinanceiraService;

    @Mock
    private CategoriaService categoriaService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private TransacaoMapper transacaoMapper;

    @Mock
    private FaturaService faturaService;

    @Mock
    private TransacaoRecorrenteRepository transacaoRecorrenteRepository;

    @InjectMocks
    private TransacaoService transacaoService;

    private UsuarioEntity usuarioPadrao;
    private CategoriaEntity categoriaDespesa;
    private ContaEntity contaPadrao;
    private CartaoEntity cartaoPadrao;

    @BeforeEach
    void setUp() {
        usuarioPadrao = new UsuarioEntity();
        usuarioPadrao.setId(1L);

        categoriaDespesa = new CategoriaEntity();
        categoriaDespesa.setId(5L);
        categoriaDespesa.setTipo(TipoTransacao.DESPESA);
        categoriaDespesa.setNome("Alimentação");

        contaPadrao = new ContaEntity();
        contaPadrao.setId(10L);
        contaPadrao.setSaldo(new BigDecimal("1000.00"));

        cartaoPadrao = new CartaoEntity();
        cartaoPadrao.setId(20L);
    }

    @Test
    void deveCriarTransacaoEmContaComSucesso() {
        TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                "Mercado", new BigDecimal("100.00"), LocalDate.now(), TipoTransacao.DESPESA,
                StatusTransacao.PAGO, MetodoPagamento.PIX, 10L, null, 5L, null, null, null, "key-1");

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(categoriaService.buscarPorIdOuFalhar(5L, 1L)).thenReturn(categoriaDespesa);
        when(movimentacaoFinanceiraService.processarTransacaoConta(any(), any(), any(), any(), any()))
                .thenReturn(contaPadrao);

        when(transacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        TransacaoResponseDTO responseDTO = new TransacaoResponseDTO(
                100L, "Mercado", new BigDecimal("100.00"), LocalDate.now(),
                TipoTransacao.DESPESA, StatusTransacao.PAGO, MetodoPagamento.PIX,
                "Alimentação", null, "Conta Principal", null, null, null, false, null, null, false);
        when(transacaoMapper.toResponse(any())).thenReturn(responseDTO);

        TransacaoResponseDTO result = transacaoService.criarTransacao(request, 1L);

        assertThat(result).isNotNull();
        verify(movimentacaoFinanceiraService).processarTransacaoConta(TipoTransacao.DESPESA, StatusTransacao.PAGO, 10L, new BigDecimal("100.00"), 1L);
        verify(transacaoRepository).save(any(TransacaoEntity.class));
    }

    @Test
    void deveCriarTransacaoEmCartaoComSucessoEForcarStatusPendente() {
        TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                "Netflix", new BigDecimal("55.00"), LocalDate.now(), TipoTransacao.DESPESA,
                StatusTransacao.PAGO, MetodoPagamento.CARTAO_CREDITO, null, 20L, 5L, null, null, null, "key-2");

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(categoriaService.buscarPorIdOuFalhar(5L, 1L)).thenReturn(categoriaDespesa);
        
        ResultadoMovimentacaoCartao resultadoCartao = new ResultadoMovimentacaoCartao(cartaoPadrao, new FaturaEntity());
        when(movimentacaoFinanceiraService.processarDespesaCartao(eq(20L), any(), eq(new BigDecimal("55.00")), eq(1L)))
                .thenReturn(resultadoCartao);

        when(transacaoRepository.save(any())).thenAnswer(i -> {
            TransacaoEntity t = i.getArgument(0);
            assertThat(t.getStatus()).isEqualTo(StatusTransacao.PENDENTE);
            return t;
        });

        transacaoService.criarTransacao(request, 1L);

        verify(movimentacaoFinanceiraService).processarDespesaCartao(eq(20L), any(), eq(new BigDecimal("55.00")), eq(1L));
        verify(transacaoRepository).save(any());
    }

    @Test
    void deveLancarExcecaoSeInformarContaECartao() {
        TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                "Erro", BigDecimal.TEN, LocalDate.now(), TipoTransacao.DESPESA,
                null, null, 10L, 20L, 5L, null, null, null, "key-3");

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);

        assertThatThrownBy(() -> transacaoService.criarTransacao(request, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("não pode pertencer a uma conta bancária e a um cartão");
    }

    @Test
    void deveLancarExcecaoSeCategoriaForIncompativelComTipo() {
        categoriaDespesa.setTipo(TipoTransacao.RECEITA);
        TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                "Erro", BigDecimal.TEN, LocalDate.now(), TipoTransacao.DESPESA,
                null, null, 10L, null, 5L, null, null, null, "key-4");

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(categoriaService.buscarPorIdOuFalhar(5L, 1L)).thenReturn(categoriaDespesa);

        assertThatThrownBy(() -> transacaoService.criarTransacao(request, 1L))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("não pode ser usada em transação do tipo DESPESA");
    }

    @Test
    void deveDeletarTransacaoEReverterImpacto() {
        TransacaoEntity transacao = new TransacaoEntity();
        transacao.setId(100L);
        transacao.setUsuario(usuarioPadrao);
        transacao.setAtivo(true);

        when(transacaoRepository.findById(100L)).thenReturn(Optional.of(transacao));

        transacaoService.deletarTransacao(100L, 1L);

        assertThat(transacao.isAtivo()).isFalse();
        verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(transacao, 1L);
        verify(transacaoRepository).save(transacao);
    }

    @Test
    void deveListarPaginado() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 2);
        TransacaoEntity t1 = new TransacaoEntity(); t1.setId(1L);
        TransacaoEntity t2 = new TransacaoEntity(); t2.setId(2L);
        org.springframework.data.domain.Page<TransacaoEntity> pageEnt =
                new org.springframework.data.domain.PageImpl<>(List.of(t1, t2), pageable, 2);
        when(transacaoRepository.findByUsuarioId(1L, pageable)).thenReturn(pageEnt);
        when(transacaoMapper.toResponse(t1)).thenReturn(new TransacaoResponseDTO(1L, "Desc1", BigDecimal.ZERO, LocalDate.now(), TipoTransacao.DESPESA, StatusTransacao.PENDENTE, MetodoPagamento.PIX, null, null, null, null, null, null, false, null, null, false));
        when(transacaoMapper.toResponse(t2)).thenReturn(new TransacaoResponseDTO(2L, "Desc2", BigDecimal.ZERO, LocalDate.now(), TipoTransacao.DESPESA, StatusTransacao.PENDENTE, MetodoPagamento.PIX, null, null, null, null, null, null, false, null, null, false));

        var result = transacaoService.listarPorUsuario(1L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        verify(transacaoRepository).findByUsuarioId(1L, pageable);
    }

    @Test
    void deveAtualizarTransacaoDesfazendoAntigaEAplicandoNova() {
        TransacaoEntity transacaoAntiga = new TransacaoEntity();
        transacaoAntiga.setId(100L);
        transacaoAntiga.setUsuario(usuarioPadrao);
        transacaoAntiga.setValor(new BigDecimal("50.00"));

        TransacaoRegistroRequestDTO requestNovo = new TransacaoRegistroRequestDTO(
                "Novo", new BigDecimal("100.00"), LocalDate.now(), TipoTransacao.DESPESA,
                StatusTransacao.PAGO, MetodoPagamento.PIX, 10L, null, 5L, null, null, null, "key-5");

        when(transacaoRepository.findById(100L)).thenReturn(Optional.of(transacaoAntiga));
        when(categoriaService.buscarPorIdOuFalhar(5L, 1L)).thenReturn(categoriaDespesa);
        when(movimentacaoFinanceiraService.processarTransacaoConta(any(), any(), any(), any(), any()))
                .thenReturn(contaPadrao);

        transacaoService.atualizarTransacao(100L, requestNovo, 1L);

        verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(transacaoAntiga, 1L);
        verify(movimentacaoFinanceiraService).processarTransacaoConta(TipoTransacao.DESPESA, StatusTransacao.PAGO, 10L, new BigDecimal("100.00"), 1L);
        verify(transacaoRepository).save(transacaoAntiga);
    }

    @Test
    void deveLancarExcecaoSeTransacaoNaoPertencerAoUsuario() {
        TransacaoEntity transacao = new TransacaoEntity();
        UsuarioEntity outroUsuario = new UsuarioEntity();
        outroUsuario.setId(99L);
        transacao.setUsuario(outroUsuario);

        when(transacaoRepository.findById(100L)).thenReturn(Optional.of(transacao));

        assertThatThrownBy(() -> transacaoService.deletarTransacao(100L, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("não pertence ao usuário");
    }
    @Test
    void deveLancarExcecaoSeIdempotencyKeyJaExistir() {
        TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                "Mercado", new BigDecimal("100.00"), LocalDate.now(), TipoTransacao.DESPESA,
                StatusTransacao.PAGO, MetodoPagamento.PIX, 10L, null, 5L, null, null, null, "unique-key");

        when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
        when(transacaoRepository.existsByIdempotencyKey("unique-key")).thenReturn(true);

        assertThatThrownBy(() -> transacaoService.criarTransacao(request, 1L))
                .isInstanceOf(OperacaoNaoPermitidaException.class)
                .hasMessageContaining("Esta transação já foi processada anteriormente.");
    }

        @Test
        void deveListarPorIntervaloComSucesso() {
                LocalDate inicio = LocalDate.of(2026, 1, 1);
                LocalDate fim = LocalDate.of(2026, 3, 31);

                TransacaoEntity t1 = new TransacaoEntity();
                t1.setId(1L);
                t1.setData(LocalDate.of(2026, 3, 15));

                TransacaoEntity t2 = new TransacaoEntity();
                t2.setId(2L);
                t2.setData(LocalDate.of(2026, 1, 10));

                when(transacaoRepository.findByUsuarioIdAndDataBetween(1L, inicio, fim))
                                .thenReturn(List.of(t1, t2));
                when(transacaoMapper.toResponse(any(TransacaoEntity.class))).thenReturn(
                                new TransacaoResponseDTO(1L, "D1", BigDecimal.ZERO, LocalDate.of(2026, 3, 15),
                                                TipoTransacao.DESPESA, StatusTransacao.PENDENTE, MetodoPagamento.PIX,
                                                null, null, null, null, null, null, false, null, null, false));

                List<TransacaoResponseDTO> resultado = transacaoService.listarPorIntervalo(inicio, fim, 1L);

                assertThat(resultado).hasSize(2);
                verify(transacaoRepository).findByUsuarioIdAndDataBetween(1L, inicio, fim);
        }

        @Test
        void deveLancarExcecaoSeDataFimAnteriorADataInicio() {
                LocalDate inicio = LocalDate.of(2026, 3, 1);
                LocalDate fim = LocalDate.of(2026, 1, 1);

                assertThatThrownBy(() -> transacaoService.listarPorIntervalo(inicio, fim, 1L))
                                .isInstanceOf(RegraDeNegocioException.class)
                                .hasMessageContaining("dataFim nao pode ser anterior");
        }

        @Test
        void deveLancarExcecaoSeIntervaloMaiorQue12Meses() {
                LocalDate inicio = LocalDate.of(2024, 1, 1);
                LocalDate fim = LocalDate.of(2026, 3, 1);

                assertThatThrownBy(() -> transacaoService.listarPorIntervalo(inicio, fim, 1L))
                                .isInstanceOf(RegraDeNegocioException.class)
                                .hasMessageContaining("Intervalo maximo");
        }

        @Test
        void deveCriarTransacaoComRecorrenteIdEVincularARecorrencia() {
                TransacaoRecorrenteEntity recorrencia = new TransacaoRecorrenteEntity();
                recorrencia.setId(7L);

                TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                        "Salário", new BigDecimal("3000.00"), LocalDate.now(), TipoTransacao.RECEITA,
                        null, MetodoPagamento.PIX, 10L, null, null, 7L, null, null, "REC-7-2026-05");

                when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
                when(movimentacaoFinanceiraService.processarTransacaoConta(any(), any(), any(), any(), any()))
                        .thenReturn(contaPadrao);
                when(transacaoRecorrenteRepository.findByIdAndUsuarioId(7L, 1L)).thenReturn(Optional.of(recorrencia));

                when(transacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                when(transacaoMapper.toResponse(any())).thenReturn(new TransacaoResponseDTO(
                        50L, "Salário", new BigDecimal("3000.00"), LocalDate.now(), TipoTransacao.RECEITA,
                        StatusTransacao.PAGO, MetodoPagamento.PIX, null, null, "Conta Principal", 10L, null, null, true, null, null, false));

                TransacaoResponseDTO result = transacaoService.criarTransacao(request, 1L);

                assertThat(result.isRecorrente()).isTrue();
                verify(transacaoRecorrenteRepository).findByIdAndUsuarioId(7L, 1L);
                verify(transacaoRepository).save(argThat(t -> t.getRecorrente() != null && Long.valueOf(7L).equals(t.getRecorrente().getId())));
        }

        @Test
        void deveCriarTransacaoSemRecorrenteIdComRecorrenteNulo() {
                TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                        "Compra Manual", new BigDecimal("50.00"), LocalDate.now(), TipoTransacao.DESPESA,
                        StatusTransacao.PAGO, MetodoPagamento.PIX, 10L, null, 5L, null, null, null, "manual-1");

                when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
                when(categoriaService.buscarPorIdOuFalhar(5L, 1L)).thenReturn(categoriaDespesa);
                when(movimentacaoFinanceiraService.processarTransacaoConta(any(), any(), any(), any(), any()))
                        .thenReturn(contaPadrao);

                when(transacaoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
                when(transacaoMapper.toResponse(any())).thenReturn(new TransacaoResponseDTO(
                        51L, "Compra Manual", new BigDecimal("50.00"), LocalDate.now(), TipoTransacao.DESPESA,
                        StatusTransacao.PAGO, MetodoPagamento.PIX, "Alimentação", 5L, "Conta Principal", 10L, null, null, false, null, null, false));

                TransacaoResponseDTO result = transacaoService.criarTransacao(request, 1L);

                assertThat(result.isRecorrente()).isFalse();
                verify(transacaoRepository).save(argThat(t -> t.getRecorrente() == null));
        }

        @Test
        void deveGerarNParcelasParaCompraNoCartaoComRateioExato() {
                TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                        "Mercado", new BigDecimal("200.00"), LocalDate.of(2026, 5, 30), TipoTransacao.DESPESA,
                        null, MetodoPagamento.CARTAO_CREDITO, null, 20L, 5L, null, null, 3, "key-parc");

                when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);
                when(categoriaService.buscarPorIdOuFalhar(5L, 1L)).thenReturn(categoriaDespesa);
                when(movimentacaoFinanceiraService.processarDespesaCartao(eq(20L), any(), any(), eq(1L)))
                        .thenReturn(new ResultadoMovimentacaoCartao(cartaoPadrao, new FaturaEntity()));

                List<TransacaoEntity> salvas = new ArrayList<>();
                when(transacaoRepository.save(any())).thenAnswer(i -> {
                        salvas.add(i.getArgument(0));
                        return i.getArgument(0);
                });
                when(transacaoMapper.toResponse(any())).thenReturn(new TransacaoResponseDTO(
                        1L, "Mercado", new BigDecimal("66.67"), LocalDate.of(2026, 5, 30), TipoTransacao.DESPESA,
                        StatusTransacao.PENDENTE, MetodoPagamento.CARTAO_CREDITO, null, null, null, null, null, 20L, false, 1, 3, false));

                transacaoService.criarTransacao(request, 1L);

                assertThat(salvas).hasSize(3);
                assertThat(salvas).extracting(TransacaoEntity::getValor)
                        .containsExactly(new BigDecimal("66.67"), new BigDecimal("66.67"), new BigDecimal("66.66"));
                assertThat(salvas).extracting(TransacaoEntity::getNumeroParcela).containsExactly(1, 2, 3);
                assertThat(salvas).extracting(TransacaoEntity::getTotalParcelas).containsExactly(3, 3, 3);
                assertThat(salvas).extracting(TransacaoEntity::getStatus)
                        .containsExactly(StatusTransacao.PENDENTE, StatusTransacao.PENDENTE, StatusTransacao.PENDENTE);
                assertThat(salvas).extracting(TransacaoEntity::getData).containsExactly(
                        LocalDate.of(2026, 5, 30), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 7, 30));

                BigDecimal soma = salvas.stream().map(TransacaoEntity::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(soma).isEqualByComparingTo("200.00");

                UUID grupo = salvas.get(0).getGrupoParcelamento();
                assertThat(grupo).isNotNull();
                assertThat(salvas).allMatch(t -> grupo.equals(t.getGrupoParcelamento()));
                assertThat(salvas).extracting(TransacaoEntity::getIdempotencyKey)
                        .containsExactly("key-parc-p1", "key-parc-p2", "key-parc-p3");

                verify(movimentacaoFinanceiraService, times(3)).processarDespesaCartao(eq(20L), any(), any(), eq(1L));
        }

        @Test
        void deveBloquearParcelamentoEmReceita() {
                TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                        "Erro", new BigDecimal("100.00"), LocalDate.now(), TipoTransacao.RECEITA,
                        null, MetodoPagamento.CARTAO_CREDITO, null, 20L, null, null, null, 3, "key-r");

                when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);

                assertThatThrownBy(() -> transacaoService.criarTransacao(request, 1L))
                        .isInstanceOf(RegraDeNegocioException.class)
                        .hasMessageContaining("Parcelamento só é permitido em despesas");
        }

        @Test
        void deveExcluirTodasAsParcelasQuandoEscopoGrupo() {
                UUID grupo = UUID.randomUUID();
                TransacaoEntity p1 = new TransacaoEntity();
                p1.setId(100L); p1.setUsuario(usuarioPadrao); p1.setAtivo(true); p1.setGrupoParcelamento(grupo);
                TransacaoEntity p2 = new TransacaoEntity();
                p2.setId(101L); p2.setUsuario(usuarioPadrao); p2.setAtivo(true); p2.setGrupoParcelamento(grupo);
                TransacaoEntity p3 = new TransacaoEntity();
                p3.setId(102L); p3.setUsuario(usuarioPadrao); p3.setAtivo(true); p3.setGrupoParcelamento(grupo);

                when(transacaoRepository.findById(100L)).thenReturn(Optional.of(p1));
                when(transacaoRepository.findByGrupoParcelamentoAndUsuarioId(grupo, 1L))
                        .thenReturn(List.of(p1, p2, p3));

                transacaoService.deletarTransacao(100L, 1L, true);

                assertThat(p1.isAtivo()).isFalse();
                assertThat(p2.isAtivo()).isFalse();
                assertThat(p3.isAtivo()).isFalse();
                verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(p1, 1L);
                verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(p2, 1L);
                verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(p3, 1L);
                verify(transacaoRepository, times(3)).save(any(TransacaoEntity.class));
        }

        @Test
        void naoDeveExcluirTransacaoComFaturaPaga() {
                FaturaEntity faturaPaga = new FaturaEntity();
                faturaPaga.setStatus(StatusFatura.PAGA);
                TransacaoEntity t = new TransacaoEntity();
                t.setId(100L); t.setUsuario(usuarioPadrao); t.setAtivo(true); t.setFatura(faturaPaga);

                when(transacaoRepository.findById(100L)).thenReturn(Optional.of(t));

                assertThatThrownBy(() -> transacaoService.deletarTransacao(100L, 1L))
                        .isInstanceOf(RegraDeNegocioException.class)
                        .hasMessageContaining("fatura já foi paga");
                verify(movimentacaoFinanceiraService, never()).desfazerEfeitoFinanceiro(any(), any());
                verify(transacaoRepository, never()).save(any());
        }

        @Test
        void deveExcluirApenasUmaParcelaQuandoEscopoUnica() {
                UUID grupo = UUID.randomUUID();
                TransacaoEntity p1 = new TransacaoEntity();
                p1.setId(100L); p1.setUsuario(usuarioPadrao); p1.setAtivo(true); p1.setGrupoParcelamento(grupo);

                when(transacaoRepository.findById(100L)).thenReturn(Optional.of(p1));

                transacaoService.deletarTransacao(100L, 1L, false);

                assertThat(p1.isAtivo()).isFalse();
                verify(movimentacaoFinanceiraService).desfazerEfeitoFinanceiro(p1, 1L);
                verify(transacaoRepository, never()).findByGrupoParcelamentoAndUsuarioId(any(), any());
                verify(transacaoRepository, times(1)).save(p1);
        }

        @Test
        void deveBloquearParcelamentoEmContaBancaria() {
                TransacaoRegistroRequestDTO request = new TransacaoRegistroRequestDTO(
                        "Erro", new BigDecimal("100.00"), LocalDate.now(), TipoTransacao.DESPESA,
                        StatusTransacao.PAGO, MetodoPagamento.PIX, 10L, null, null, null, null, 3, "key-c");

                when(usuarioService.buscarPorIdOuFalhar(1L)).thenReturn(usuarioPadrao);

                assertThatThrownBy(() -> transacaoService.criarTransacao(request, 1L))
                        .isInstanceOf(RegraDeNegocioException.class)
                        .hasMessageContaining("cartão de crédito");
        }
}
