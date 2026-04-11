package org.app_financeiro.backend.service;

import org.app_financeiro.backend.dto.response.HgFinanceResponseDTO;
import java.util.Optional;

/**
 * Interface para comunicação com a API HG Brasil Finance.
 * SOLID: Segregação de Interface para o serviço de cotações externas.
 */
public interface HgFinanceClient {
    /**
     * Busca os dados financeiros detalhados (Moedas e Taxas) da HG Brasil.
     *
     * @return Opcional com os dados mapeados em caso de sucesso.
     */
    Optional<HgFinanceResponseDTO> fetchFinanceData();
}
