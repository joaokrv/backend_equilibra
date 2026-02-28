package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Armazena os códigos de verificação de e-mail.
 * Cada código tem validade de 15 minutos e só pode ser usado uma vez.
 */
@Data
@Entity
@Table(name = "codigos_verificacao")
public class CodigoVerificacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 6)
    private String codigo;

    @Column(nullable = false)
    private LocalDateTime dataExpiracao;

    @Column(nullable = false)
    private boolean utilizado = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;
}
