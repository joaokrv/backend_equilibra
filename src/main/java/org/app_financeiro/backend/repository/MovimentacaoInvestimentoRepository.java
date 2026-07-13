package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.dto.response.MovimentacaoInvestimentoResponseDTO;
import org.app_financeiro.backend.entity.MovimentacaoInvestimentoEntity;
import org.app_financeiro.backend.enums.TipoMovimentacaoInvestimento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovimentacaoInvestimentoRepository extends JpaRepository<MovimentacaoInvestimentoEntity, Long> {

    /**
     * Extrato paginado com JOIN para nomes — resolve N+1 via projeção JPQL.
     * Filtros tipo e investimentoId são opcionais (:param IS NULL ignora o filtro).
     */
    @Query("""
        SELECT new org.app_financeiro.backend.dto.response.MovimentacaoInvestimentoResponseDTO(
            m.id, m.tipo, m.valor, m.data,
            i.descricao, m.investimentoId,
            c.nome, m.observacao)
        FROM MovimentacaoInvestimentoEntity m
        JOIN InvestimentoEntity i ON i.id = m.investimentoId
        LEFT JOIN ContaEntity c ON c.id = m.contaId
        WHERE m.usuarioId = :usuarioId
          AND m.ativo = true
          AND (:tipo IS NULL OR m.tipo = :tipo)
          AND (:investimentoId IS NULL OR m.investimentoId = :investimentoId)
          AND m.data BETWEEN :dataInicio AND :dataFim
        ORDER BY m.data DESC, m.dataCriacao DESC
        """)
    Page<MovimentacaoInvestimentoResponseDTO> buscarExtrato(
            @Param("usuarioId") Long usuarioId,
            @Param("tipo") TipoMovimentacaoInvestimento tipo,
            @Param("investimentoId") Long investimentoId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim,
            Pageable pageable);

    /** Preview: últimas 5 movimentações de todos os investimentos do usuário. */
    @Query("""
        SELECT new org.app_financeiro.backend.dto.response.MovimentacaoInvestimentoResponseDTO(
            m.id, m.tipo, m.valor, m.data,
            i.descricao, m.investimentoId,
            c.nome, m.observacao)
        FROM MovimentacaoInvestimentoEntity m
        JOIN InvestimentoEntity i ON i.id = m.investimentoId
        LEFT JOIN ContaEntity c ON c.id = m.contaId
        WHERE m.usuarioId = :usuarioId
          AND m.ativo = true
        ORDER BY m.data DESC, m.dataCriacao DESC
        """)
    List<MovimentacaoInvestimentoResponseDTO> buscarPreview(
            @Param("usuarioId") Long usuarioId,
            Pageable pageable);

    /** Busca por id + usuarioId para validação de ownership (IDOR). */
    Optional<MovimentacaoInvestimentoEntity> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Localiza em lote as movimentações originadas por transações — 1 query para o desfazer inteiro. */
    List<MovimentacaoInvestimentoEntity> findByTransacaoIdInAndUsuarioIdAndAtivoTrue(
            Collection<Long> transacaoIds, Long usuarioId);
}
