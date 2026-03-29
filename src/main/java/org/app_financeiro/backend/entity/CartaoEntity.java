package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import org.app_financeiro.backend.enums.BandeiraCartao;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * Entidade que representa um Cartão de Crédito do usuário.
 *
 * O campo "limite" armazena o limite total contratado do cartão.
 * O limite disponível NÃO é armazenado aqui — ele é calculado dinamicamente
 * pelo CartaoService com base nas faturas pendentes (não PAGAS).
 *
 * Os dias de fechamento e vencimento são usados pelo FaturaService
 * para determinar a qual fatura uma transação pertence e calcular as datas corretas.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "cartoes")
@SQLRestriction("ativo = true")
public class CartaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BandeiraCartao bandeira = BandeiraCartao.OUTROS;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal limite;

    @Column
    private Integer diaFechamento;

    @Column
    private Integer diaVencimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntity usuario;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    private LocalDateTime dataAtualizacao;

    @Column(nullable = false)
    private boolean ativo = true;
}
