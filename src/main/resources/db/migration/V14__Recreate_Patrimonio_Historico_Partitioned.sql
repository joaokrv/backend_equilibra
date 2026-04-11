-- V14: Recriação de Patrimonio Historico com Particionamento
DROP TABLE IF EXISTS patrimonio_historico CASCADE;

CREATE TABLE patrimonio_historico (
    id BIGSERIAL NOT NULL,
    usuario_id BIGINT NOT NULL,
    valor_total NUMERIC(19, 2) NOT NULL,
    data_referencia DATE NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_patrimonio_historico PRIMARY KEY (id, data_referencia),
    CONSTRAINT fk_patrimonio_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) PARTITION BY RANGE (data_referencia);

-- Criação da partição inicial (2026)
CREATE TABLE patrimonio_historico_2026 PARTITION OF patrimonio_historico
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE INDEX idx_patrimonio_usuario_data ON patrimonio_historico (usuario_id, data_referencia);
