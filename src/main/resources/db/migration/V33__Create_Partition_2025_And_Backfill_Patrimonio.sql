-- V33: Create partition for 2025 and backfill patrimonio_historico with 180 days of history

-- Create partition for 2025
CREATE TABLE IF NOT EXISTS patrimonio_historico_2025 PARTITION OF patrimonio_historico
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- Backfill snapshots for last 180 days using efficient generate_series
INSERT INTO patrimonio_historico (id, usuario_id, data_referencia, valor_total, saldo_contas, total_investido)
SELECT
  nextval('patrimonio_historico_id_seq'),
  u.id,
  d.data_referencia,
  COALESCE((SELECT COALESCE(SUM(saldo), 0) FROM contas WHERE usuario_id = u.id AND ativo = true), 0) +
  COALESCE((SELECT COALESCE(SUM(valor_atual), 0) FROM investimentos WHERE usuario_id = u.id AND ativo = true), 0),
  COALESCE((SELECT COALESCE(SUM(saldo), 0) FROM contas WHERE usuario_id = u.id AND ativo = true), 0),
  COALESCE((SELECT COALESCE(SUM(valor_atual), 0) FROM investimentos WHERE usuario_id = u.id AND ativo = true), 0)
FROM usuarios u
CROSS JOIN (
  SELECT (CURRENT_DATE - INTERVAL '180 days' + (i || ' days')::INTERVAL)::DATE as data_referencia
  FROM generate_series(0, 180) i
) d
WHERE u.ativo = true AND u.email_verificado = true;
