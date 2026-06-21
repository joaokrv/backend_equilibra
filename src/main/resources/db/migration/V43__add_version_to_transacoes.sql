-- V43: lock otimista na tabela de transacoes.
-- Previne dupla reversao de saldo/limite quando duas requisicoes concorrentes
-- (DELETE/PUT) atuam sobre a mesma transacao (double-spend reverso).
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
