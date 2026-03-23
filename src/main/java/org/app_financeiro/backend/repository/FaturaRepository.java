package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.FaturaEntity;
import org.app_financeiro.backend.enums.StatusFatura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.app_financeiro.backend.dto.projections.DividaCartaoProjection;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    /**
     * Soma todas as dívidas pendentes de todos os cartões de um usuário em uma única query.
     * Usado para evitar o problema N+1 ao listar cartões.
     *
     * @param usuarioId ID do usuário
     * @param status    Status a excluir da soma (ex: PAGA)
     * @return Lista de projeções com ID do cartão e sua dívida total
     */
    @Query("SELECT new org.app_financeiro.backend.dto.projections.DividaCartaoProjection(f.cartao.id, SUM(f.valorTotal - f.valorPago)) " +
           "FROM FaturaEntity f WHERE f.cartao.usuario.id = :usuarioId AND f.status != :status GROUP BY f.cartao.id")
    List<DividaCartaoProjection> somarDividasPorCartoes(@Param("usuarioId") Long usuarioId, @Param("status") StatusFatura status);

    /**
     * Atualiza o status de faturas não pagas que já passaram do vencimento para ATRASADA.
     *
     * @param hoje           Data de referência (normalmente LocalDate.now())
     * @param statusAtrasada Status ATRASADA
     * @param statusAberta   Status ABERTA
     * @param statusFechada  Status FECHADA
     * @return Número de faturas atualizadas
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE FaturaEntity f SET f.status = :statusAtrasada WHERE f.status IN (:statusAberta, :statusFechada) AND f.dataVencimento < :hoje")
    int marcarFaturasComoAtrasadas(
            @Param("hoje") LocalDate hoje,
            @Param("statusAtrasada") StatusFatura statusAtrasada,
            @Param("statusAberta") StatusFatura statusAberta,
            @Param("statusFechada") StatusFatura statusFechada
    );
}
