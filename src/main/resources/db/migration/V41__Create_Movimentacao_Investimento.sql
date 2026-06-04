-- @Version em investimentos (locking otimista, consistente com contas/cartoes/faturas)
ALTER TABLE investimentos ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Tabela de movimentacoes de investimento (fonte unica do extrato)
CREATE TABLE IF NOT EXISTS movimentacao_investimento (
    id               BIGSERIAL     PRIMARY KEY,
    investimento_id  BIGINT        NOT NULL REFERENCES investimentos(id),
    usuario_id       BIGINT        NOT NULL REFERENCES usuarios(id),
    tipo             VARCHAR(15)   NOT NULL,          -- APORTE | RESGATE | RENDIMENTO
    valor            DECIMAL(19,2) NOT NULL,           -- pode ser negativo (rendimento de perda)
    data             DATE          NOT NULL,
    conta_id         BIGINT        REFERENCES contas(id),     -- null em RENDIMENTO
    transacao_id     BIGINT        REFERENCES transacoes(id), -- null em RENDIMENTO
    observacao       VARCHAR(255),
    ativo            BOOLEAN       NOT NULL DEFAULT TRUE,
    data_criacao     TIMESTAMP     NOT NULL DEFAULT NOW(),
    data_atualizacao TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mov_inv_usuario_data  ON movimentacao_investimento (usuario_id, data DESC);
CREATE INDEX IF NOT EXISTS idx_mov_inv_investimento  ON movimentacao_investimento (investimento_id);
CREATE INDEX IF NOT EXISTS idx_mov_inv_transacao     ON movimentacao_investimento (transacao_id);
