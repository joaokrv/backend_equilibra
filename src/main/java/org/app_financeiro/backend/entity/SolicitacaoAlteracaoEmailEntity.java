package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Armazena solicitações de alteração de e-mail.
 * Fluxo em 2 etapas: solicitar (envia OTP ao novo email) → confirmar (valida OTP e efetiva troca).
 * Cada código tem validade de 15 minutos e só pode ser usado uma vez.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "solicitacoes_alteracao_email")
public class SolicitacaoAlteracaoEmailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "novo_email", nullable = false)
    private String novoEmail;

    @Column(nullable = false, length = 6)
    private String codigo;

    @Column(nullable = false)
    private LocalDateTime dataExpiracao;

    @Column(name = "utilizado", nullable = false)
    private boolean isUtilizado = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;
}
