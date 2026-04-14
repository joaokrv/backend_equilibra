-- Adiciona colunas para persistir saldo de contas e total investido separadamente.
-- Registros antigos terao valor 0 (default). Dados reais a partir do proximo snapshot.
ALTER TABLE patrimonio_historico
    ADD COLUMN saldo_contas NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN total_investido NUMERIC(15,2) NOT NULL DEFAULT 0;
