ALTER TABLE transacoes
    ADD COLUMN IF NOT EXISTS grupo_parcelamento UUID;

CREATE INDEX IF NOT EXISTS idx_transacao_grupo
    ON transacoes (grupo_parcelamento);

COMMENT ON COLUMN transacoes.grupo_parcelamento IS
    'Agrupa as parcelas de uma mesma compra parcelada no cartão. Nulo para compras à vista ou em conta.';
