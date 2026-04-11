CREATE TABLE solicitacoes_alteracao_email (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    novo_email VARCHAR(255) NOT NULL,
    codigo VARCHAR(6) NOT NULL,
    data_expiracao TIMESTAMP NOT NULL,
    utilizado BOOLEAN NOT NULL DEFAULT FALSE,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_solic_email_usuario ON solicitacoes_alteracao_email(usuario_id);
