-- V37: Adiciona coluna tipo em codigos_verificacao para diferenciar o propósito do OTP.
-- Necessário para o fluxo de exclusão/desativação/reativação de conta.
-- DEFAULT 'VERIFICACAO_EMAIL' mantém compatibilidade com todos os registros existentes.

ALTER TABLE codigos_verificacao
    ADD COLUMN tipo VARCHAR(30) NOT NULL DEFAULT 'VERIFICACAO_EMAIL';

COMMENT ON COLUMN codigos_verificacao.tipo IS
    'Propósito do OTP: VERIFICACAO_EMAIL, EXCLUSAO_CONTA, DESATIVACAO_CONTA, REATIVACAO_CONTA';

CREATE INDEX idx_codigos_verificacao_tipo ON codigos_verificacao(email, tipo);
