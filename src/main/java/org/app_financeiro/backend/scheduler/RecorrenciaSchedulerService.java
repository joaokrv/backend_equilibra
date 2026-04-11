package org.app_financeiro.backend.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.app_financeiro.backend.dto.request.TransacaoRegistroRequestDTO;
import org.app_financeiro.backend.entity.TransacaoRecorrenteEntity;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.repository.RecorrenciaCanceladaRepository;
import org.app_financeiro.backend.repository.TransacaoRecorrenteRepository;
import org.app_financeiro.backend.repository.TransacaoRepository;
import org.app_financeiro.backend.service.TransacaoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class RecorrenciaSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(RecorrenciaSchedulerService.class);

    private final TransacaoRecorrenteRepository recorrenteRepository;
    private final RecorrenciaCanceladaRepository canceladaRepository;
    private final TransacaoRepository transacaoRepository;
    private final TransacaoService transacaoService;

    public RecorrenciaSchedulerService(TransacaoRecorrenteRepository recorrenteRepository,
                                        RecorrenciaCanceladaRepository canceladaRepository,
                                        TransacaoRepository transacaoRepository,
                                        TransacaoService transacaoService) {
        this.recorrenteRepository = recorrenteRepository;
        this.canceladaRepository = canceladaRepository;
        this.transacaoRepository = transacaoRepository;
        this.transacaoService = transacaoService;
    }

    @Scheduled(cron = "0 0 6 * * ?")
    @SchedulerLock(name = "processar-recorrencias", lockAtMostFor = "5m", lockAtLeastFor = "1m")
    public void processarRecorrenciasDoDia() {
        LocalDate hoje = LocalDate.now();
        processarParaData(hoje);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verificarPendentesNoStartup() {
        LocalDate hoje = LocalDate.now();
        int dia = Math.min(hoje.getDayOfMonth(), YearMonth.from(hoje).lengthOfMonth());
        List<TransacaoRecorrenteEntity> recorrencias = recorrenteRepository.findAtivasParaProcessar(dia, hoje);
        if (!recorrencias.isEmpty()) {
            log.info("Verificando {} recorrências pendentes no startup para dia {}", recorrencias.size(), dia);
            processarParaData(hoje);
        }
    }

    private void processarParaData(LocalDate data) {
        int diaReal = data.getDayOfMonth();
        int ultimoDiaMes = YearMonth.from(data).lengthOfMonth();

        // Busca recorrências cujo dia é hoje OU cujo dia > último dia do mês (ex: dia 31 em fevereiro)
        List<TransacaoRecorrenteEntity> recorrencias = recorrenteRepository.findAtivasParaProcessar(diaReal, data);

        // Também processa recorrências com dia > último dia do mês no último dia do mês
        if (diaReal == ultimoDiaMes) {
            for (int dia = ultimoDiaMes + 1; dia <= 31; dia++) {
                recorrencias.addAll(recorrenteRepository.findAtivasParaProcessar(dia, data));
            }
        }

        int geradas = 0;
        int puladas = 0;

        for (TransacaoRecorrenteEntity rec : recorrencias) {
            try {
                int ano = data.getYear();
                int mes = data.getMonthValue();

                // Verificar se o mês está cancelado
                if (canceladaRepository.existsByRecorrenteIdAndAnoAndMes(rec.getId(), ano, mes)) {
                    puladas++;
                    continue;
                }

                // Verificar idempotência — evita duplicata
                String idempotencyKey = "REC-" + rec.getId() + "-" + ano + "-" + mes;
                if (transacaoRepository.existsByIdempotencyKey(idempotencyKey)) {
                    puladas++;
                    continue;
                }

                // Determinar data real da transação
                int diaLancamento = Math.min(rec.getDiaLancamento(), ultimoDiaMes);
                LocalDate dataTransacao = LocalDate.of(ano, mes, diaLancamento);

                TransacaoRegistroRequestDTO dto = new TransacaoRegistroRequestDTO(
                        rec.getDescricao(),
                        rec.getValor(),
                        dataTransacao,
                        rec.getTipo(),
                        StatusTransacao.PENDENTE,
                        rec.getMetodoPagamento(),
                        rec.getConta().getId(),
                        rec.getCartao() != null ? rec.getCartao().getId() : null,
                        rec.getCategoria() != null ? rec.getCategoria().getId() : null,
                        null, // numeroParcela
                        null, // totalParcelas
                        idempotencyKey
                );

                transacaoService.criarTransacao(dto, rec.getUsuario().getId());
                geradas++;
            } catch (Exception e) {
                log.error("Erro ao processar recorrência {} para {}/{}: {}", rec.getId(), data.getMonthValue(), data.getYear(), e.getMessage());
            }
        }

        if (geradas > 0 || puladas > 0) {
            log.info("Recorrências processadas: {} geradas, {} puladas", geradas, puladas);
        }
    }
}
