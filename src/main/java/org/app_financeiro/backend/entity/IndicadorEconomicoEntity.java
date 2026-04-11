package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entidade que armazena indicadores econômicos e taxas de mercado (SELIC, CDI, IPCA, Câmbio).
 * Utiliza particionamento mensal no PostgreSQL para otimização de performance e limpeza.
 */
@Entity
@Table(name = "indicador_economico")
@IdClass(IndicadorEconomicoId.class)
@Getter
@Setter
@NoArgsConstructor
public class IndicadorEconomicoEntity {

    @Id
    private Long id;

    @Column(name = "nome", nullable = false, length = 50)
    private String nome;

    @Column(name = "valor", nullable = false, precision = 19, scale = 4)
    private BigDecimal valor;

    @Column(name = "variacao", precision = 10, scale = 4)
    private BigDecimal variacao;

    @Id
    @Column(name = "data_atualizacao", nullable = false)
    private LocalDate dataAtualizacao;

    @Column(name = "provedor", nullable = false, length = 50)
    private String provedor;

    /**
     * Construtor de conveniência para criação rápida de indicadores.
     */
    public IndicadorEconomicoEntity(String nome, BigDecimal valor, BigDecimal variacao, LocalDate dataAtualizacao, String provedor) {
        this.nome = nome;
        this.valor = valor;
        this.variacao = variacao;
        this.dataAtualizacao = dataAtualizacao;
        this.provedor = provedor;
    }
}
