-- Tabela de sessões de importação: persiste candidatas para revisão e permite desfazer
CREATE TABLE IF NOT EXISTS importacoes (
    id                UUID        PRIMARY KEY,
    usuario_id        BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    formato_detectado VARCHAR(20) NOT NULL,
    candidatas        JSONB,
    total_candidatas  INTEGER     NOT NULL DEFAULT 0,
    total_duplicatas  INTEGER     NOT NULL DEFAULT 0,
    data_criacao      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_importacoes_usuario_id ON importacoes(usuario_id);

-- Liga transações à importação que as criou; SET NULL mantém transação se importação for expurgada
ALTER TABLE transacoes
    ADD COLUMN IF NOT EXISTS importacao_id UUID NULL REFERENCES importacoes(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_transacoes_importacao_id ON transacoes(importacao_id);

-- Marca faturas criadas pela importação como quitação histórica (nascem PAGA sem débito de conta)
ALTER TABLE faturas
    ADD COLUMN IF NOT EXISTS quitacao_historica BOOLEAN NOT NULL DEFAULT FALSE;
