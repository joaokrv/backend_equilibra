package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.app_financeiro.backend.enums.FormatoDetectado;
import org.app_financeiro.backend.enums.StatusImportacao;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "importacoes")
public class ImportacaoEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntity usuario;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private StatusImportacao status = StatusImportacao.PENDENTE;

    @Column(name = "formato_detectado", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private FormatoDetectado formatoDetectado;

    /** JSON com as candidatas para revisão. Nulo após confirmação. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String candidatas;

    @Column(nullable = false)
    private Integer totalCandidatas = 0;

    @Column(nullable = false)
    private Integer totalDuplicatas = 0;

    /** Instante do claim atômico para PROCESSANDO. Permite destravar sessões presas por crash. */
    @Column(name = "processando_em")
    private LocalDateTime processandoEm;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCriacao;
}
