-- Habilita similaridade de texto para detecção de duplicatas na importação
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Rastreia a origem de cada transação (MANUAL, IMPORTACAO_CSV, IMPORTACAO_PDF)
ALTER TABLE transacoes ADD COLUMN IF NOT EXISTS origem VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
