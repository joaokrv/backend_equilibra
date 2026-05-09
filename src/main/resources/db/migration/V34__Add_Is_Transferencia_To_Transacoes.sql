ALTER TABLE transacoes
    ADD COLUMN IF NOT EXISTS is_transferencia BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE transacoes
SET is_transferencia = TRUE
WHERE is_transferencia = FALSE
  AND metodo_pagamento = 'TRANSFERENCIA'
  AND cartao_id IS NULL
  AND categoria_id IS NULL
  AND (
        descricao LIKE 'Aporte%investimento:%'
        OR descricao LIKE 'Resgate de investimento:%'
      );