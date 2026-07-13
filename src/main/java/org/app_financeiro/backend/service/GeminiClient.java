package org.app_financeiro.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.app_financeiro.backend.exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class GeminiClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.max-chamadas-diarias:20}")
    private int maxChamadasDiarias;

    @Value("${gemini.max-chamadas-diarias-usuario:5}")
    private int maxChamadasDiariasUsuario;

    // Contador em memória: válido para deploy de instância única; reseta em restart.
    // O teto absoluto é o RPD da conta Google (free tier: 500/dia com billing desabilitado).
    private final AtomicInteger chamadasHoje = new AtomicInteger(0);
    // Cota por usuário: sem isso, um usuário (ou 4 IPs sob o bucket de upload) esgota
    // sozinho o teto global e nega o serviço para todos os demais.
    private final Map<Long, AtomicInteger> chamadasHojePorUsuario = new ConcurrentHashMap<>();
    private volatile LocalDate diaContador = LocalDate.now();

    public GeminiClient(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.restTemplate = builder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .build();
        this.objectMapper = objectMapper;
    }

    /** Verifica e incrementa os contadores diários (global e por usuário). Thread-safe via sincronização leve. */
    private synchronized void verificarLimiteDiario(Long usuarioId) {
        LocalDate hoje = LocalDate.now();
        if (!hoje.equals(diaContador)) {
            diaContador = hoje;
            chamadasHoje.set(0);
            chamadasHojePorUsuario.clear();
            log.info("Contador diário Gemini resetado para o dia {}", hoje);
        }

        int atual = chamadasHoje.incrementAndGet();
        if (atual > maxChamadasDiarias) {
            chamadasHoje.decrementAndGet();
            log.warn("Limite diário Gemini atingido: {}/{} chamadas", atual - 1, maxChamadasDiarias);
            throw new RegraDeNegocioException(
                    "error.importacao.gemini_limite_diario",
                    "Limite diário de processamento por IA atingido. Tente novamente amanhã.");
        }

        AtomicInteger contadorUsuario = chamadasHojePorUsuario.computeIfAbsent(usuarioId, id -> new AtomicInteger(0));
        int atualUsuario = contadorUsuario.incrementAndGet();
        if (atualUsuario > maxChamadasDiariasUsuario) {
            contadorUsuario.decrementAndGet();
            chamadasHoje.decrementAndGet();
            log.warn("Limite diário Gemini do usuário atingido: usuarioId={}, {}/{} chamadas",
                    usuarioId, atualUsuario - 1, maxChamadasDiariasUsuario);
            throw new RegraDeNegocioException(
                    "error.importacao.gemini_limite_diario_usuario",
                    "Você atingiu o limite diário de importações por IA. Tente novamente amanhã.");
        }

        log.debug("Chamada Gemini {}/{} do dia {} (usuário {}/{})",
                atual, maxChamadasDiarias, hoje, atualUsuario, maxChamadasDiariasUsuario);
    }

    /**
     * Envia documento (PDF ou imagem) ao Gemini 2.5 Flash e retorna o texto da resposta.
     * O chamador é responsável por definir o prompt e parsear o JSON retornado.
     *
     * @param conteudo  bytes do arquivo
     * @param mimeType  ex.: "application/pdf", "image/jpeg"
     * @param prompt    instrução textual enviada junto ao documento
     * @param usuarioId usuário que solicitou a extração — aplica a cota individual, além da global
     * @return texto bruto retornado pelo modelo (esperado: JSON estruturado)
     */
    public String extrairTexto(byte[] conteudo, String mimeType, String prompt, Long usuarioId) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RegraDeNegocioException(
                    "error.importacao.gemini_nao_configurado",
                    "Integração com IA não configurada. Contate o suporte.");
        }
        verificarLimiteDiario(usuarioId);

        String base64 = Base64.getEncoder().encodeToString(conteudo);

        Map<String, Object> inlineData = Map.of("mimeType", mimeType, "data", base64);
        Map<String, Object> partArquivo = Map.of("inlineData", inlineData);
        Map<String, Object> partTexto = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(partArquivo, partTexto));

        Map<String, Object> generationConfig = Map.of(
                "responseMimeType", "application/json",
                "temperature", 0
        );

        Map<String, Object> body = Map.of(
                "contents", List.of(content),
                "generationConfig", generationConfig
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Chave via header, nunca query string: exceções de rede (ResourceAccessException)
        // incluem a URL completa na mensagem e vazariam a chave nos logs.
        headers.set("x-goog-api-key", apiKey);

        try {
            String responseBody = restTemplate.postForObject(
                    ENDPOINT,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            JsonNode root = objectMapper.readTree(responseBody);
            String texto = root.at("/candidates/0/content/parts/0/text").asText();

            if (texto.isBlank()) {
                log.warn("Gemini retornou resposta vazia para mimeType={}", mimeType);
                throw new RegraDeNegocioException(
                        "error.importacao.gemini_resposta_vazia",
                        "O modelo de IA não retornou dados. Tente novamente.");
            }

            return texto;

        } catch (HttpClientErrorException e) {
            log.error("Erro HTTP ao chamar Gemini: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RegraDeNegocioException(
                    "error.importacao.gemini_erro_api",
                    "Falha na integração com IA. Tente novamente.");
        } catch (RegraDeNegocioException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro inesperado ao chamar Gemini", e);
            throw new RegraDeNegocioException(
                    "error.importacao.gemini_erro_inesperado",
                    "Erro inesperado ao processar documento. Tente novamente.");
        }
    }
}
