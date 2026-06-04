-- Opt-out de lembretes de fatura por usuário
ALTER TABLE usuarios
    ADD COLUMN notificacoes_fatura_ativo BOOLEAN NOT NULL DEFAULT TRUE;

-- Tabela de idempotência e histórico de notificações de fatura
CREATE TABLE notificacao_fatura (
    id                BIGSERIAL PRIMARY KEY,
    fatura_id         BIGINT       NOT NULL,
    usuario_id        BIGINT       NOT NULL,
    tipo              VARCHAR(10)  NOT NULL,
    scheduled_at      DATE         NOT NULL,
    sent_at           TIMESTAMP,
    status            VARCHAR(10)  NOT NULL DEFAULT 'PENDENTE',
    erro              TEXT,
    idempotency_key   VARCHAR(100) NOT NULL,
    data_criacao      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_notificacao_fatura    FOREIGN KEY (fatura_id)  REFERENCES faturas(id),
    CONSTRAINT fk_notificacao_usuario   FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT uq_notificacao_key       UNIQUE (idempotency_key)
);

CREATE INDEX idx_notificacao_fatura_fatura  ON notificacao_fatura (fatura_id);
CREATE INDEX idx_notificacao_fatura_usuario ON notificacao_fatura (usuario_id);
CREATE INDEX idx_notificacao_fatura_status  ON notificacao_fatura (status);
