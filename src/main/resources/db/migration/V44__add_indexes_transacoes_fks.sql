-- V44: indices nas FKs de transacoes. O PostgreSQL nao cria indice automatico
-- para chaves estrangeiras; sem eles, findByFaturaId/countByFaturaId (filtram so
-- por fatura_id, sem usuario_id) e as operacoes em cascata fazem sequential scan
-- na maior tabela do sistema. Indices parciais (IS NOT NULL) pois muitas linhas
-- tem essas FKs nulas.
CREATE INDEX IF NOT EXISTS idx_transacao_fatura ON transacoes (fatura_id) WHERE fatura_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_transacao_cartao ON transacoes (cartao_id) WHERE cartao_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_transacao_conta ON transacoes (conta_id) WHERE conta_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_transacao_categoria ON transacoes (categoria_id) WHERE categoria_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_transacao_recorrente ON transacoes (recorrente_id) WHERE recorrente_id IS NOT NULL;
