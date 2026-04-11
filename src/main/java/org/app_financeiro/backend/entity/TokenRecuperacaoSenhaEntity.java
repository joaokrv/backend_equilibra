package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entidade que armazena tokens de recuperação de senha.
 *
 * Cada token é um UUID único enviado por e-mail ao usuário.
 * O token possui validade de 30 minutos e só pode ser utilizado uma vez.
 * Tokens anteriores são automaticamente invalidados quando um novo é gerado.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "tokens_recuperacao_senha")
public class TokenRecuperacaoSenhaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime dataExpiracao;

    @Column(name = "utilizado", nullable = false)
    private boolean isUtilizado = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;
}
