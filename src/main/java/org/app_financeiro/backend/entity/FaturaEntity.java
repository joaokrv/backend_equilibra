package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.app_financeiro.backend.enums.StatusFatura;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidade que representa a Fatura mensal de um Cartão de Crédito.
 *
 * Faturas são criadas de forma "Lazy" (sob demanda) pelo FaturaService:
 * elas só existem quando a primeira transação do mês é registrada no cartão.
 *
 * O campo "valorTotal" é incrementado a cada nova transação e decrementado
 * quando uma transação é removida. O "valorPago" acompanha pagamentos parciais.
 *
 * Ciclo de vida do status:
 *   ABERTA -> (passou dataFechamento) -> FECHADA
 *   FECHADA -> (passou dataVencimento) -> ATRASADA
 *   FECHADA ou ATRASADA -> (paga integralmente) -> PAGA
 *
 * O índice único em (cartao_id + mes + ano) garante que nunca
 * existirão duas faturas para o mesmo cartão no mesmo mês/ano.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "faturas", indexes = {
    @Index(name = "idx_fatura_cartao_mes_ano", columnList = "cartao_id, mes, ano", unique = true)
})
@SQLRestriction("ativo = true")
public class FaturaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id", nullable = false)
    private CartaoEntity cartao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntity usuario;

    @Column(nullable = false)
    private Integer mes;

    @Column(nullable = false)
    private Integer ano;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorPago = BigDecimal.ZERO;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusFatura status = StatusFatura.ABERTA;

    @Column(nullable = false)
    private LocalDate dataVencimento;

    @Column(nullable = false)
    private LocalDate dataFechamento;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    private LocalDateTime dataAtualizacao;

    @Column(nullable = false)
    private boolean ativo = true;
}
