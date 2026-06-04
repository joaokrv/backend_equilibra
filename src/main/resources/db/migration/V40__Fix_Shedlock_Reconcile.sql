-- V5 e V13 criavam a tabela shedlock (V5 sem IF NOT EXISTS, gerando ambiguidade).
-- Esta migration garante que a tabela existe com o schema correto de forma idempotente,
-- independente de qual das duas anteriores foi executada no ambiente.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
