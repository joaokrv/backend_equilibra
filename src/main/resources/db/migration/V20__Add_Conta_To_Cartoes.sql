-- V20: Vincula um cartão de crédito a uma conta bancária (opcional).
-- Essa associação é usada para sugerir automaticamente a conta de débito
-- ao pagar a fatura do cartão.
ALTER TABLE cartoes
    ADD COLUMN IF NOT EXISTS conta_id BIGINT,
    ADD CONSTRAINT fk_cartoes_conta FOREIGN KEY (conta_id) REFERENCES contas(id);
