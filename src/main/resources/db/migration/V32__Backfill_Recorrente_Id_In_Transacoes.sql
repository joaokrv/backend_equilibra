-- Popula recorrente_id em transações geradas pelo scheduler
-- Identifica pela convenção idempotencyKey = 'REC-{recorrenteId}-{ano}-{mes}'
-- Extrai recorrenteId uma única vez com CTE para evitar repetição de SUBSTRING
WITH parsed AS (
  SELECT
    id,
    CAST(
      SUBSTRING(idempotency_key, 5, POSITION('-' IN SUBSTRING(idempotency_key, 5)) - 1)
      AS BIGINT
    ) AS rec_id
  FROM transacoes
  WHERE idempotency_key ~ '^REC-[0-9]+-'
    AND recorrente_id IS NULL
)
UPDATE transacoes t
SET recorrente_id = p.rec_id
FROM parsed p
WHERE t.id = p.id
  AND EXISTS (SELECT 1 FROM transacoes_recorrentes tr WHERE tr.id = p.rec_id);
