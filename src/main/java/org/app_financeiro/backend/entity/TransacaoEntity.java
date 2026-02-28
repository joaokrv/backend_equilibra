package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.app_financeiro.backend.enums.MetodoPagamento;
import org.app_financeiro.backend.enums.StatusTransacao;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "transacoes", indexes = {
    @Index(name = "idx_transacao_usuario_data", columnList = "usuario_id, data")
})
public class TransacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_id")
    private ContaEntity conta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cartao_id")
    private CartaoEntity cartao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fatura_id")
    private FaturaEntity fatura;

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

    @Column
    private Integer numeroParcela;

    @Column
    private Integer totalParcelas;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    private LocalDateTime dataAtualizacao;

    @Column(nullable = false)
    private boolean ativo = true;
}
