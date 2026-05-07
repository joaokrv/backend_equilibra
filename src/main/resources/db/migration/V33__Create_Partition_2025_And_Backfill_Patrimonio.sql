-- 1. Cria a partição para 2025
CREATE TABLE IF NOT EXISTS patrimonio_historico_2025 PARTITION OF patrimonio_historico
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

-- 2. Backfill: Maio 2026 (mês atual)
INSERT INTO patrimonio_historico (id, usuario_id, data_referencia, valor_total, saldo_contas, total_investido)
SELECT nextval('patrimonio_historico_id_seq'), u.id, d::DATE, 0, 0, 0
FROM usuarios u
CROSS JOIN generate_series('2026-05-01'::DATE, '2026-05-07'::DATE, INTERVAL '1 day') d
WHERE u.ativo = true AND u.email_verificado = true;

-- 3. Backfill: Abril 2026
INSERT INTO patrimonio_historico (id, usuario_id, data_referencia, valor_total, saldo_contas, total_investido)
SELECT nextval('patrimonio_historico_id_seq'), u.id, d::DATE, 0, 0, 0
FROM usuarios u
CROSS JOIN generate_series('2026-04-01'::DATE, '2026-04-30'::DATE, INTERVAL '1 day') d
WHERE u.ativo = true AND u.email_verificado = true;

-- 4. Backfill: Março 2026
INSERT INTO patrimonio_historico (id, usuario_id, data_referencia, valor_total, saldo_contas, total_investido)
SELECT nextval('patrimonio_historico_id_seq'), u.id, d::DATE, 0, 0, 0
FROM usuarios u
CROSS JOIN generate_series('2026-03-01'::DATE, '2026-03-31'::DATE, INTERVAL '1 day') d
WHERE u.ativo = true AND u.email_verificado = true;
