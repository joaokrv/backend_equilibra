ALTER TABLE investimentos ADD COLUMN tipo_investimento VARCHAR(40) NOT NULL DEFAULT 'OUTRO';
ALTER TABLE investimentos ADD COLUMN tipo_personalizado VARCHAR(60);
