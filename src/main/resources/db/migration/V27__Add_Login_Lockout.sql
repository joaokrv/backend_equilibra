-- V27: Account lockout após múltiplas tentativas de login (G4-A1)
-- Após 10 tentativas erradas, conta fica bloqueada por 15 minutos.

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS login_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;

COMMENT ON COLUMN usuarios.locked_until IS
    'Data e hora até as quais uma conta ficará bloqueada após múltiplas tentativas de login falhadas.';
