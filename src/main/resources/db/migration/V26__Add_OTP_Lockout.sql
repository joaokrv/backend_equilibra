-- V26: OTP lockout counter per code (B1-C1)
-- After 5 failed attempts the code is invalidated, forcing a resend.

ALTER TABLE codigos_verificacao
    ADD COLUMN tentativas_falhas INT NOT NULL DEFAULT 0;

ALTER TABLE solicitacoes_alteracao_email
    ADD COLUMN tentativas_falhas INT NOT NULL DEFAULT 0;
