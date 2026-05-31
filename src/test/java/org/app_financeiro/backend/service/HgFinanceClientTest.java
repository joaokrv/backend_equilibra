package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.HgFinanceResponseDTO;
import org.app_financeiro.backend.service.impl.HgFinanceClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Teste de integração para o cliente da HG Brasil.
 * TDD: Validação do parsing do JSON e do contrato com a API externa.
 */
@RestClientTest(HgFinanceClientImpl.class)
@org.springframework.test.context.TestPropertySource(properties = "hg.api-key=")
class HgFinanceClientTest {

    @Autowired
    private HgFinanceClient hgFinanceClient;

    @Autowired
    private MockRestServiceServer server;

    private String jsonMock;

    @BeforeEach
    void setUp() {
        jsonMock = """
        {
          "valid_key": true,
          "results": {
            "currencies": {
              "USD": { "name": "Dollar", "buy": 5.48, "variation": 0.12 },
              "EUR": { "name": "Euro", "buy": 6.12, "variation": -0.05 }
            },
            "taxes": [
              { "date": "2024-03-29", "cdi": 10.65, "selic": 10.75 }
            ]
          }
        }
        """;
    }

    @Test
    @DisplayName("Deve realizar parse correto da resposta da HG Brasil")
    void deveFazerParseCorretoDaResposta() {
        server.expect(requestTo("https://api.hgbrasil.com/finance?key="))
              .andRespond(withSuccess(jsonMock, MediaType.APPLICATION_JSON));

        Optional<HgFinanceResponseDTO> response = hgFinanceClient.fetchFinanceData();

        assertTrue(response.isPresent());
        HgFinanceResponseDTO.Results results = response.get().results();
        
        assertEquals(new BigDecimal("5.48"), results.currencies().get("USD").buy());
        assertEquals(new BigDecimal("10.75"), results.taxes().get(0).selic());
        assertEquals(new BigDecimal("10.65"), results.taxes().get(0).cdi());
    }
}
