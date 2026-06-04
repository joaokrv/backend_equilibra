-- Backfill de aportes e resgates historicos a partir da tabela transacoes.
-- Criterio de match: descricao LIKE 'Aporte%investimento%' ou 'Resgate%investimento%'
-- Extrai nome do investimento apos o ': ' e cruza com investimentos do mesmo usuario.
-- Empate de nomes: vincula ao investimento mais antigo (MIN id).
-- Transacoes sem match determinístico ficam de fora (nao inventa vinculo).

INSERT INTO movimentacao_investimento
    (investimento_id, usuario_id, tipo, valor, data, conta_id, transacao_id, ativo, data_criacao, data_atualizacao)
SELECT
    inv_match.investimento_id,
    t.usuario_id,
    CASE
        WHEN t.descricao ILIKE 'Resgate%investimento%' THEN 'RESGATE'
        ELSE 'APORTE'
    END AS tipo,
    t.valor,
    t.data,
    t.conta_id,
    t.id,
    TRUE,
    NOW(),
    NOW()
FROM transacoes t
INNER JOIN LATERAL (
    SELECT MIN(i.id) AS investimento_id
    FROM investimentos i
    WHERE i.usuario_id = t.usuario_id
      AND i.ativo = TRUE
      AND i.descricao = TRIM(
          SUBSTRING(t.descricao FROM POSITION(': ' IN t.descricao) + 2)
      )
) inv_match ON inv_match.investimento_id IS NOT NULL
WHERE t.is_transferencia = TRUE
  AND t.ativo = TRUE
  AND POSITION(': ' IN t.descricao) > 0
  AND (
      t.descricao ILIKE 'Aporte%investimento%'
   OR t.descricao ILIKE 'Resgate%investimento%'
  )
  -- evita duplicata caso migration seja re-executada parcialmente
  AND NOT EXISTS (
      SELECT 1 FROM movimentacao_investimento m
      WHERE m.transacao_id = t.id
  );
