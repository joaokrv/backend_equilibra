package org.app_financeiro.backend.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.app_financeiro.backend.dto.response.HgFinanceResponseDTO;
import org.app_financeiro.backend.service.HgFinanceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Optional;

/**
 * Implementação do cliente de integração com a HG Brasil Finance.
 * SOLID: Responsabilidade de comunicação externa isolada.
 */
@Slf4j
@Service
public class HgFinanceClientImpl implements HgFinanceClient {

    private final RestTemplate restTemplate;

    @Value("${hg.api-key:}")
    private String hgApiKey;

    private static final String BASE_URL = "https://api.hgbrasil.com/finance";

    public HgFinanceClientImpl(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Optional<HgFinanceResponseDTO> fetchFinanceData() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .queryParam("key", hgApiKey)
                    .toUriString();

            log.debug("Iniciando requisição para HG Brasil Finance...");
            HgFinanceResponseDTO response = restTemplate.getForObject(url, HgFinanceResponseDTO.class);

            if (response != null && response.validKey()) {
                log.debug("Dados financeiros obtidos com sucesso da HG Brasil.");
                return Optional.of(response);
            } else {
                log.warn("A chave da API HG Brasil retornou como inválida ou houve falha na resposta.");
            }
        } catch (Exception e) {
            log.error("Erro técnico ao consumir API HG Brasil Finance: {}", e.getMessage());
        }
        return Optional.empty();
    }
}
