package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * Entidade que representa uma Categoria de transação do usuário.
 *
 * Categorias são usadas para classificar transações (ex: Alimentação, Lazer, Salário).
 * Cada categoria pertence a um único usuário e tem um tipo fixo (RECEITA ou DESPESA),
 * impedindo que uma categoria de despesa seja usada em uma receita e vice-versa.
 *
 * O campo "ativo" controla o Soft Delete: ao desativar uma categoria, transações já
 * vinculadas mantêm a referência histórica. Apenas novas transações são impedidas
 * de selecionar uma categoria inativa.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "categorias")
@SQLRestriction("ativo = true")
public class CategoriaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoTransacao tipo;

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

    @Column(name = "is_padrao", nullable = false)
    private boolean isPadrao = false;
}
