package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/**
 * Entidade que representa um Usuário do sistema.
 * É a raiz de toda a hierarquia de dados: contas, cartões, faturas e transações
 * são sempre vinculados a um usuário específico.
 *
 * O campo "ativo" controla o Soft Delete — ao invés de apagar o registro,
 * ele é desativado para preservar o histórico financeiro.
 * O campo "emailVerificado" exige confirmação via código OTP antes de permitir login.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "usuarios")
public class UsuarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column(nullable = false)
    private boolean emailVerificado = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    private LocalDateTime dataAtualizacao;

    @Column(nullable = false)
    private boolean ativo = true;
}
