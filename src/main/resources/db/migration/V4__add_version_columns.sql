-- Migration V4: add version columns for optimistic locking
ALTER TABLE contas ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE cartoes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE faturas ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
