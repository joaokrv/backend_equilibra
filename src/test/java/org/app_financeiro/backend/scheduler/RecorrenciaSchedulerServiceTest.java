package org.app_financeiro.backend.scheduler;

import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.TransacaoRecorrenteEntity;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.app_financeiro.backend.repository.RecorrenciaCanceladaRepository;
import org.app_financeiro.backend.repository.TransacaoRecorrenteRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.service.TransacaoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa a geração de transações a partir de recorrências (hot path):
 * idempotência, meses cancelados e resiliência por-item. As datas usam LocalDate.now()
 * para espelhar exatamente o que o scheduler computa no dia da execução.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecorrenciaSchedulerServiceTest {

    @Mock private TransacaoRecorrenteRepository recorrenteRepository;
    @Mock private RecorrenciaCanceladaRepository canceladaRepository;
    @Mock private TransacaoRepository transacaoRepository;
    @Mock private TransacaoService transacaoService;

    private RecorrenciaSchedulerService scheduler() {
        return new RecorrenciaSchedulerService(
                recorrenteRepository, canceladaRepository, transacaoRepository, transacaoService);
    }

    private TransacaoRecorrenteEntity recorrenciaDeConta(long id) {
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setId(1L);

        ContaEntity conta = new ContaEntity();
        conta.setId(5L);

        TransacaoRecorrenteEntity rec = new TransacaoRecorrenteEntity();
        rec.setId(id);
        rec.setDescricao("Assinatura");
        rec.setValor(new BigDecimal("29.90"));
        rec.setTipo(TipoTransacao.DESPESA);
        rec.setMetodoPagamento(MetodoPagamento.PIX);
        rec.setConta(conta);
        rec.setDiaLancamento(10);
        rec.setUsuario(usuario);
        rec.setAtivo(true);
        return rec;
    }

    /** Mocka a busca para retornar a recorrência apenas no dia real de hoje (demais dias: lista vazia). */
    private void mockBuscaParaHoje(TransacaoRecorrenteEntity... recs) {
        int diaReal = LocalDate.now().getDayOfMonth();
        when(recorrenteRepository.findAtivasParaProcessar(eq(diaReal), any()))
                .thenReturn(new ArrayList<>(List.of(recs)));
    }

    @Test
    void deveGerarTransacaoComIdempotencyKeyEsperada() {
        TransacaoRecorrenteEntity rec = recorrenciaDeConta(42L);
        mockBuscaParaHoje(rec);
        when(canceladaRepository.existsByRecorrenteIdAndAnoAndMes(anyLong(), anyInt(), anyInt())).thenReturn(false);
        when(transacaoRepository.existsByIdempotencyKey(anyString())).thenReturn(false);

        scheduler().processarRecorrenciasDoDia();

        LocalDate hoje = LocalDate.now();
        String chaveEsperada = "REC-42-" + hoje.getYear() + "-" + hoje.getMonthValue();

        ArgumentCaptor<TransacaoRegistroRequestDTO> captor = ArgumentCaptor.forClass(TransacaoRegistroRequestDTO.class);
        verify(transacaoService).criarTransacao(captor.capture(), eq(1L));

        TransacaoRegistroRequestDTO dto = captor.getValue();
        assertThat(dto.recorrenteId()).isEqualTo(42L);
        assertThat(dto.idempotencyKey()).isEqualTo(chaveEsperada);
        assertThat(dto.contaId()).isEqualTo(5L);
        assertThat(dto.cartaoId()).isNull();
    }

    @Test
    void devePularQuandoMesEstaCancelado() {
        mockBuscaParaHoje(recorrenciaDeConta(7L));
        when(canceladaRepository.existsByRecorrenteIdAndAnoAndMes(anyLong(), anyInt(), anyInt())).thenReturn(true);

        scheduler().processarRecorrenciasDoDia();

        verify(transacaoService, never()).criarTransacao(any(), anyLong());
    }

    @Test
    void devePularQuandoIdempotencyKeyJaExiste() {
        mockBuscaParaHoje(recorrenciaDeConta(7L));
        when(canceladaRepository.existsByRecorrenteIdAndAnoAndMes(anyLong(), anyInt(), anyInt())).thenReturn(false);
        when(transacaoRepository.existsByIdempotencyKey(anyString())).thenReturn(true);

        scheduler().processarRecorrenciasDoDia();

        verify(transacaoService, never()).criarTransacao(any(), anyLong());
    }

    @Test
    void erroEmUmaRecorrenciaNaoAbortaAsDemais() {
        TransacaoRecorrenteEntity falha = recorrenciaDeConta(1L);
        TransacaoRecorrenteEntity ok = recorrenciaDeConta(2L);
        mockBuscaParaHoje(falha, ok);
        when(canceladaRepository.existsByRecorrenteIdAndAnoAndMes(anyLong(), anyInt(), anyInt())).thenReturn(false);
        when(transacaoRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(transacaoService.criarTransacao(any(), anyLong()))
                .thenThrow(new RuntimeException("falha simulada"))
                .thenReturn(null);

        assertThatCode(() -> scheduler().processarRecorrenciasDoDia()).doesNotThrowAnyException();

        verify(transacaoService, times(2)).criarTransacao(any(), anyLong());
    }
}
