package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import org.app_financeiro.backend.enums.StatusNotificacaoFatura;
import org.app_financeiro.backend.enums.TipoLembreteFatura;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacao_fatura")
public class NotificacaoFaturaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fatura_id", nullable = false)
    private Long faturaId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoLembreteFatura tipo;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDate scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusNotificacaoFatura status = StatusNotificacaoFatura.PENDENTE;

    @Column(columnDefinition = "TEXT")
    private String erro;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    public Long getId() { return id; }
    public Long getFaturaId() { return faturaId; }
    public void setFaturaId(Long faturaId) { this.faturaId = faturaId; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public TipoLembreteFatura getTipo() { return tipo; }
    public void setTipo(TipoLembreteFatura tipo) { this.tipo = tipo; }
    public LocalDate getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDate scheduledAt) { this.scheduledAt = scheduledAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public StatusNotificacaoFatura getStatus() { return status; }
    public void setStatus(StatusNotificacaoFatura status) { this.status = status; }
    public String getErro() { return erro; }
    public void setErro(String erro) { this.erro = erro; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
