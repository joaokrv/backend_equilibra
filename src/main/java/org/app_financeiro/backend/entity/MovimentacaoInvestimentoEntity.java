package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import org.app_financeiro.backend.enums.TipoMovimentacaoInvestimento;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Log de movimentações de investimento. Fonte única do extrato de investimentos. */
@Entity
@Table(name = "movimentacao_investimento")
public class MovimentacaoInvestimentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK sem gerenciamento JPA — evita cascade acidental. */
    @Column(name = "investimento_id", nullable = false)
    private Long investimentoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoMovimentacaoInvestimento tipo;

    /** Valor do movimento. Negativo para rendimento de perda. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate data;

    /** Null para RENDIMENTO (sem movimentação de conta). */
    @Column(name = "conta_id")
    private Long contaId;

    /** Null para RENDIMENTO (não gera transação em transacoes). */
    @Column(name = "transacao_id")
    private Long transacaoId;

    @Column(length = 255)
    private String observacao;

    @Column(nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;

    public Long getId() { return id; }
    public Long getInvestimentoId() { return investimentoId; }
    public void setInvestimentoId(Long investimentoId) { this.investimentoId = investimentoId; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public TipoMovimentacaoInvestimento getTipo() { return tipo; }
    public void setTipo(TipoMovimentacaoInvestimento tipo) { this.tipo = tipo; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public Long getContaId() { return contaId; }
    public void setContaId(Long contaId) { this.contaId = contaId; }
    public Long getTransacaoId() { return transacaoId; }
    public void setTransacaoId(Long transacaoId) { this.transacaoId = transacaoId; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
}
