package org.app_financeiro.backend.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * ID Composto para a entidade PatrimonioHistoricoEntity.
 * A data de referência é obrigatória na PK para compatibilidade com o particionamento PostgreSQL.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PatrimonioHistoricoId implements Serializable {
    private Long id;
    private LocalDate dataReferencia;
}
