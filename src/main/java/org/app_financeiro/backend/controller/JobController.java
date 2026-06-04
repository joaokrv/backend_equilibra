package org.app_financeiro.backend.controller;

import jakarta.annotation.PostConstruct;
import org.app_financeiro.backend.dto.response.JobResultDTO;
import org.app_financeiro.backend.service.FaturaLembreteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/internal/jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final FaturaLembreteService faturaLembreteService;

    @Value("${job.token:}")
    private String jobToken;

    public JobController(FaturaLembreteService faturaLembreteService) {
        this.faturaLembreteService = faturaLembreteService;
    }

    @PostConstruct
    void validarConfiguracaoToken() {
        if (jobToken == null || jobToken.isBlank()) {
            log.warn("[SECURITY] JOB_TOKEN não configurado — endpoint /internal/jobs/** rejeitará TODAS as requisições. "
                    + "Configure a env var JOB_TOKEN para ativar os jobs internos (ex: lembrete de fatura).");
        }
    }

    @PostMapping("/faturas/lembrar")
    public ResponseEntity<?> executarLembreteFaturas(
            @RequestHeader(value = "X-Job-Token", required = false) String tokenRecebido) {

        if (!tokenValido(tokenRecebido)) {
            log.warn("Tentativa de acesso ao job com token inválido");
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        log.info("Job de lembrete de fatura iniciado via cron externo");
        JobResultDTO resultado = faturaLembreteService.executarJob();
        return ResponseEntity.ok(resultado);
    }

    private boolean tokenValido(String tokenRecebido) {
        if (jobToken == null || jobToken.isBlank()) return false;
        if (tokenRecebido == null || tokenRecebido.isBlank()) return false;
        return MessageDigest.isEqual(
                tokenRecebido.getBytes(),
                jobToken.getBytes()
        );
    }
}
