CREATE TABLE patrimonio_historico (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    valor_total NUMERIC(19, 2) NOT NULL,
    data_referencia DATE NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_patrimonio_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT uk_usuario_data_referencia UNIQUE (usuario_id, data_referencia)
);

CREATE INDEX idx_patrimonio_usuario_data ON patrimonio_historico (usuario_id, data_referencia);
