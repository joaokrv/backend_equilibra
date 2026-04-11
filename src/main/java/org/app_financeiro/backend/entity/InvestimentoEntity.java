package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.app_financeiro.backend.enums.TipoInvestimento;
import java.math.BigDecimal;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * Entidade que representa uma Meta de Investimento/Poupança do usuário.
 *
 * Registra o progresso de uma meta financeira ao longo do tempo.
 * O "valorInicial" é o ponto de partida definido na criação.
 * O "valorAtual" é incrementado a cada depósito realizado via InvestimentoService.
 * O "metaAtual" é o valor-alvo que o usuário deseja atingir.
 *
 * Cada depósito debita o valor de uma conta bancária do usuário
 * e incrementa o valorAtual do investimento.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "investimentos")
@SQLRestriction("ativo = true")
public class InvestimentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String descricao;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorInicial;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorAtual;

    @Column(precision = 19, scale = 2)
    private BigDecimal metaAtual;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_investimento", nullable = false, length = 40)
    private TipoInvestimento tipoInvestimento;

    @Column(name = "tipo_personalizado", length = 60)
    private String tipoPersonalizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_origem_id")
    private ContaEntity contaOrigem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conta_destino_id")
    private ContaEntity contaDestino;

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
