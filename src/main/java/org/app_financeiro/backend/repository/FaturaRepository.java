package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para operações de persistência de FaturaEntity.
 */
@Repository
public interface FaturaRepository extends JpaRepository<FaturaEntity, Long> {

    /**
     * Retorna faturas de um cartão excluindo um determinado status.
     * Usado para verificar faturas em aberto antes de deletar um cartão.
     *
     * @param cartaoId ID do cartão
     * @param status   Status a excluir da busca
     * @return Lista de faturas com status diferente do informado
     */
    List<FaturaEntity> findByCartaoIdAndStatusNot(Long cartaoId, StatusFatura status);

    /**
     * Verifica se existe alguma fatura de um cartão com status diferente do informado.
     *
     * @param cartaoId ID do cartão
     * @param status   Status a excluir da verificação
     * @return true se existir pelo menos uma fatura com status diferente
     */
    boolean existsByCartaoIdAndStatusNot(Long cartaoId, StatusFatura status);

    /**
     * Retorna todas as faturas de um cartão, independentemente do status.
     *
     * @param cartaoId ID do cartão
     * @return Lista de todas as faturas do cartão
     */
    List<FaturaEntity> findByCartaoId(Long cartaoId);

    /**
     * Busca a fatura de um cartão para um mês e ano específicos.
     * Usado pelo FaturaService para localizar ou criar faturas sob demanda (Lazy Creation).
     *
     * @param cartaoId ID do cartão
     * @param mes      Mês de referência (1-12)
     * @param ano      Ano de referência
     * @return Optional com a fatura, ou vazio se não existir
     */
    Optional<FaturaEntity> findByCartaoIdAndMesAndAno(Long cartaoId, Integer mes, Integer ano);
}
