-- V35__Criar_Tabela_Usuarios_Pendentes.sql
-- Tabela de "Sala de Espera" para o fluxo de pré-registro com OTP obrigatório.

CREATE TABLE usuarios_pendentes (
    id UUID PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_em TIMESTAMP NOT NULL,
    bloqueado_ate TIMESTAMP,
    tentativas_falhas INT NOT NULL DEFAULT 0,
    ultimo_envio_em TIMESTAMP,
    ultima_tentativa_em TIMESTAMP,
    CONSTRAINT uk_usuarios_pendentes_email UNIQUE (email)
);

-- Índices para otimização de consultas e limpeza automática (housekeeping)
CREATE INDEX idx_usuarios_pendentes_email ON usuarios_pendentes(email);
CREATE INDEX idx_usuarios_pendentes_expira_em ON usuarios_pendentes(expira_em);
CREATE INDEX idx_usuarios_pendentes_bloqueado_ate ON usuarios_pendentes(bloqueado_ate);

COMMENT ON TABLE usuarios_pendentes IS 'Tabela temporária para armazenar dados de registro até a validação do e-mail via OTP.';
