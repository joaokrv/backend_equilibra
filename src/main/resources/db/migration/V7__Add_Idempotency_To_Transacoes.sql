ALTER TABLE transacoes ADD COLUMN idempotency_key VARCHAR(255);
ALTER TABLE transacoes ADD CONSTRAINT unique_idempotency_key UNIQUE (idempotency_key);
