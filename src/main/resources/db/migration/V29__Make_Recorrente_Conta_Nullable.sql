-- V29: Torna conta_id opcional em transacoes_recorrentes
-- Isso alinha o comportamento das recorrentes com as transações comuns,
-- onde uma transação pertence a uma Conta OU a um Cartão de Crédito.

ALTER TABLE transacoes_recorrentes ALTER COLUMN conta_id DROP NOT NULL;
