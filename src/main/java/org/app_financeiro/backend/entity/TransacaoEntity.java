package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/** conta XOR (cartao+fatura) — mutuamente exclusivos; categoria opcional. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "transacoes", indexes = {
    @Index(name = "idx_transacao_usuario_data", columnList = "usuario_id, data")
})
@SQLRestriction("ativo = true")
public class TransacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Lock otimista: aborta exclusão/edição concorrente da mesma transação (evita dupla reversão de saldo/limite). */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    /** Data em que a transação ocorreu (usada para calcular a qual fatura pertence). */
    @Column(nullable = false)
    private LocalDate data;

    /** Conta bancária associada. Nulo se o pagamento foi via cartão de crédito. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    private ContaEntity conta;

    /** Cartão de crédito associado. Nulo se o pagamento foi via conta bancária. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private CartaoEntity cartao;

    /** Fatura do cartão na qual esta transação foi registrada. Nulo se não for via cartão. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fatura_id")
    private FaturaEntity fatura;

    /** Categoria para classificação da transação (ex: Alimentação, Lazer). Pode ser nula. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private CategoriaEntity categoria;

    @Column
    @Enumerated(EnumType.STRING)
    private MetodoPagamento metodoPagamento;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoTransacao tipo;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusTransacao status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntity usuario;

    /** Número da parcela atual (ex: 2). Nulo se não for parcelado. */
    @Column
    private Integer numeroParcela;

    /** Total de parcelas da compra (ex: 12). Nulo se não for parcelado. */
    @Column
    private Integer totalParcelas;

    /** Agrupa as parcelas de uma mesma compra parcelada. Nulo se for compra à vista ou em conta. */
    @Column(name = "grupo_parcelamento")
    private java.util.UUID grupoParcelamento;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    private LocalDateTime dataAtualizacao;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorrente_id")
    private TransacaoRecorrenteEntity recorrente;

    @Column(name = "is_transferencia", nullable = false)
    private boolean transferencia = false;

    @Column(name = "ativo", nullable = false)
    private boolean isAtivo = true;
}
