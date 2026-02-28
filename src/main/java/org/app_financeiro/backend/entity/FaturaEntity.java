package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.app_financeiro.backend.enums.StatusFatura;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "faturas", indexes = {
    @Index(name = "idx_fatura_cartao_mes_ano", columnList = "cartao_id, mes, ano", unique = true)
})
public class FaturaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(nullable = false)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(nullable = false)
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
