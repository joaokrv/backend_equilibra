-- Tabela para armazenar tokens de recuperação de senha.
-- Cada token é um UUID único enviado por e-mail, com validade de 30 minutos.
CREATE TABLE tokens_recuperacao_senha (
    id              BIGSERIAL       PRIMARY KEY,
    email           VARCHAR(255)    NOT NULL,
    token           VARCHAR(255)    NOT NULL UNIQUE,
    data_expiracao  TIMESTAMP       NOT NULL,
    utilizado       BOOLEAN         NOT NULL DEFAULT FALSE,
    data_criacao    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Índice para buscas rápidas por token (usado na validação do link)
CREATE INDEX idx_token_recuperacao ON tokens_recuperacao_senha (token);

-- Índice para buscas por e-mail (usado na invalidação de tokens anteriores)
CREATE INDEX idx_email_recuperacao ON tokens_recuperacao_senha (email);
