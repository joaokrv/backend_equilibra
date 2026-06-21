package org.app_financeiro.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Foto de perfil em tabela própria (usuario_foto). Mantém o {@link UsuarioEntity}
 * — carregado a cada request de autenticação — leve, sem o blob da imagem.
 */
@Entity
@Table(name = "usuario_foto")
@Getter
@Setter
@NoArgsConstructor
public class UsuarioFotoEntity {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private byte[] foto;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
