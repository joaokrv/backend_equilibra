-- Garante consistencia de soft delete para dados antigos que possam ter ficado orfaos.
-- A logica de cascata ja existe nos services; este script saneia o legado.

-- 1) Investimentos ativos nao podem apontar para contas inativas.
UPDATE investimentos i
SET ativo = false,
    data_atualizacao = NOW()
WHERE i.ativo = true
  AND (
       (i.conta_origem_id IS NOT NULL AND EXISTS (
           SELECT 1 FROM contas c
           WHERE c.id = i.conta_origem_id
             AND c.ativo = false
       ))
    OR (i.conta_destino_id IS NOT NULL AND EXISTS (
           SELECT 1 FROM contas c
           WHERE c.id = i.conta_destino_id
             AND c.ativo = false
       ))
  );

-- 2) Transacoes ativas nao podem apontar para conta/cartao inativos.
UPDATE transacoes t
SET ativo = false,
    data_atualizacao = NOW()
WHERE t.ativo = true
  AND (
       (t.conta_id IS NOT NULL AND EXISTS (
           SELECT 1 FROM contas c
           WHERE c.id = t.conta_id
             AND c.ativo = false
       ))
    OR (t.cartao_id IS NOT NULL AND EXISTS (
           SELECT 1 FROM cartoes c
           WHERE c.id = t.cartao_id
             AND c.ativo = false
       ))
  );

-- 3) Recorrencias ativas nao podem apontar para conta/cartao inativos.
UPDATE transacoes_recorrentes r
SET ativo = false,
    data_atualizacao = NOW()
WHERE r.ativo = true
  AND (
       EXISTS (
           SELECT 1 FROM contas c
           WHERE c.id = r.conta_id
             AND c.ativo = false
       )
    OR (r.cartao_id IS NOT NULL AND EXISTS (
           SELECT 1 FROM cartoes c
           WHERE c.id = r.cartao_id
             AND c.ativo = false
       ))
  );

-- 4) Faturas ativas nao podem apontar para cartao inativo.
UPDATE faturas f
SET ativo = false,
    data_atualizacao = NOW()
WHERE f.ativo = true
  AND EXISTS (
      SELECT 1 FROM cartoes c
      WHERE c.id = f.cartao_id
        AND c.ativo = false
  );

-- 5) Cartao nao deve ficar vinculado a conta inativa.
UPDATE cartoes c
SET conta_id = NULL,
    data_atualizacao = NOW()
WHERE c.conta_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM contas ct
      WHERE ct.id = c.conta_id
        AND ct.ativo = false
  );

-- 6) Categoria inativa deve ser desassociada de transacoes e recorrencias ativas.
UPDATE transacoes t
SET categoria_id = NULL,
    data_atualizacao = NOW()
WHERE t.ativo = true
  AND t.categoria_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM categorias c
      WHERE c.id = t.categoria_id
        AND c.ativo = false
  );

UPDATE transacoes_recorrentes r
SET categoria_id = NULL,
    data_atualizacao = NOW()
WHERE r.ativo = true
  AND r.categoria_id IS NOT NULL
  AND EXISTS (
      SELECT 1 FROM categorias c
      WHERE c.id = r.categoria_id
        AND c.ativo = false
  );
