-- V46: padroniza as colunas monetarias de patrimonio_historico para NUMERIC(19,2),
-- regra de monetario do projeto e alinhamento com valor_total (ja 19,2) da mesma
-- tabela. ALTER na tabela-mae propaga as particoes (V14).
ALTER TABLE patrimonio_historico ALTER COLUMN saldo_contas TYPE NUMERIC(19, 2);
ALTER TABLE patrimonio_historico ALTER COLUMN total_investido TYPE NUMERIC(19, 2);
