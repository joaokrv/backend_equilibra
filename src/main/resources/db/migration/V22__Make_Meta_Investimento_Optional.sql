-- V22: Permite investimentos sem meta definida (meta_atual nullable).
ALTER TABLE investimentos
    ALTER COLUMN meta_atual DROP NOT NULL;
