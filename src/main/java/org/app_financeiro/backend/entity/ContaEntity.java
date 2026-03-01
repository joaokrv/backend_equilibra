package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * Entidade que representa uma Conta Bancária ou Carteira física do usuário.
 *
 * O saldo é modificado automaticamente pelo ContaService toda vez que
 * uma transação de RECEITA ou DESPESA é registrada.
 * Saldo negativo é bloqueado por regra de negócio — qualquer operação que
 * resultaria em saldo negativo lança SaldoInsuficienteException.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "contas")
public class ContaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private BigDecimal saldo; // Equivalente ao balance

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
