-- V45: alinha idempotency_key ao contrato da entidade (@Column nullable=false).
-- O indice UNIQUE do Postgres permite multiplos NULL, furando a garantia de
-- idempotencia se um NULL chegar ao banco. Backfill defensivo antes do NOT NULL.
UPDATE transacoes SET idempotency_key = gen_random_uuid()::text WHERE idempotency_key IS NULL;
ALTER TABLE transacoes ALTER COLUMN idempotency_key SET NOT NULL;
