package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.HgFinanceResponseDTO;
import java.util.Optional;

/** Interface segregada (SRP) para comunicação com a API HG Brasil Finance. */
public interface HgFinanceClient {
    Optional<HgFinanceResponseDTO> fetchFinanceData();
}
