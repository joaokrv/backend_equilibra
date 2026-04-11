package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "recorrencias_canceladas", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"recorrente_id", "ano", "mes"})
})
public class RecorrenciaCanceladaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorrente_id", nullable = false)
    private TransacaoRecorrenteEntity recorrente;

    @Column(nullable = false)
    private Integer ano;

    @Column(nullable = false)
    private Integer mes;
}
