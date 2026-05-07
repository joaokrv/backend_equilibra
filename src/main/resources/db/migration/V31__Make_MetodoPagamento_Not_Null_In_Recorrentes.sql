-- Preenche registros existentes sem método de pagamento
-- Via conta → PIX (padrão para receitas/despesas automáticas)
-- Via cartão → CARTAO_CREDITO
UPDATE transacoes_recorrentes
SET metodo_pagamento = CASE
    WHEN cartao_id IS NULL THEN 'PIX'
    ELSE 'CARTAO_CREDITO'
END
WHERE metodo_pagamento IS NULL;

-- Torna obrigatório para novas inserções
ALTER TABLE transacoes_recorrentes
    ALTER COLUMN metodo_pagamento SET NOT NULL;
