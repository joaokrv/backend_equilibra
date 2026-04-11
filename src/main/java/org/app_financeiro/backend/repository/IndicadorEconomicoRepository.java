package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.IndicadorEconomicoEntity;
import org.app_financeiro.backend.entity.IndicadorEconomicoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositório para gestão de indicadores econômicos (SELIC, CDI, IPCA, etc.).
 * Opera sobre a tabela particionada indicador_economico.
 */
@Repository
public interface IndicadorEconomicoRepository extends JpaRepository<IndicadorEconomicoEntity, IndicadorEconomicoId> {

    /**
     * Busca o último valor de um indicador específico.
     *
     * @param nome Nome do indicador (ex: SELIC, CDI)
     * @return Snapshot mais recente do indicador
     */
    Optional<IndicadorEconomicoEntity> findFirstByNomeOrderByDataAtualizacaoDesc(String nome);

    /**
     * Busca todos os indicadores de uma data específica.
     * Útil para carregar a barra dinâmica de uma vez.
     *
     * @param data Data de referência
     * @return Lista de indicadores do dia
     */
    List<IndicadorEconomicoEntity> findAllByDataAtualizacao(LocalDate data);

    /**
     * Busca os últimos valores conhecidos de todos os indicadores ativos.
     * Query otimizada para alimentar a barra dinâmica.
     */
    @Query("SELECT i FROM IndicadorEconomicoEntity i WHERE i.dataAtualizacao = (SELECT MAX(i2.dataAtualizacao) FROM IndicadorEconomicoEntity i2 WHERE i2.nome = i.nome)")
    List<IndicadorEconomicoEntity> buscarUltimosIndicadores();

    /**
     * Insere um indicador via native query, delegando a geração do ID ao BIGSERIAL do PostgreSQL.
     * Contorna a limitação do Hibernate que não suporta @GeneratedValue(IDENTITY) com @IdClass.
     */
    @Modifying
    @Query(value = "INSERT INTO indicador_economico (nome, valor, variacao, data_atualizacao, provedor) " +
                   "VALUES (:nome, :valor, :variacao, :dataAtualizacao, :provedor)",
           nativeQuery = true)
    void insertIndicador(@Param("nome") String nome,
                         @Param("valor") BigDecimal valor,
                         @Param("variacao") BigDecimal variacao,
                         @Param("dataAtualizacao") LocalDate dataAtualizacao,
                         @Param("provedor") String provedor);
}
