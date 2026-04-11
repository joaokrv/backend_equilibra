package org.app_financeiro.backend.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * ID Composto para a entidade IndicadorEconomicoEntity.
 * Requisito técnico para particionamento PostgreSQL no Spring Data JPA.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class IndicadorEconomicoId implements Serializable {
    private Long id;
    private LocalDate dataAtualizacao;
}
