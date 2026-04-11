-- V19: Adiciona conta de destino padrão ao investimento (usada no resgate).
ALTER TABLE investimentos ADD COLUMN conta_origem_id BIGINT REFERENCES contas(id);
ALTER TABLE investimentos ADD COLUMN conta_destino_id BIGINT REFERENCES contas(id);
