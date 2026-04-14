package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade que armazena snapshots diários do patrimônio total dos usuários.
 * Consolidado de (Saldo de Contas + Valor Atual de Investimentos).
 */
@Entity
@Table(name = "patrimonio_historico")
@IdClass(PatrimonioHistoricoId.class)
@Getter
@Setter
@NoArgsConstructor
public class PatrimonioHistoricoEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntity usuario;

    @Column(name = "valor_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal valorTotal;

    @Id
    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @Column(name = "saldo_contas", nullable = false, precision = 15, scale = 2)
    private BigDecimal saldoContas = BigDecimal.ZERO;

    @Column(name = "total_investido", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalInvestido = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "data_criacao", updatable = false)
    private LocalDateTime dataCriacao;
}
