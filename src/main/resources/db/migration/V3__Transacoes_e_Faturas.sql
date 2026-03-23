CREATE TABLE faturas (
    id BIGSERIAL PRIMARY KEY,
    cartao_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    mes INTEGER NOT NULL,
    ano INTEGER NOT NULL,
    valor_total DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    valor_pago DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(50) NOT NULL,
    data_vencimento DATE NOT NULL,
    data_fechamento DATE NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_faturas_cartao FOREIGN KEY (cartao_id) REFERENCES cartoes(id),
    CONSTRAINT fk_faturas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE UNIQUE INDEX idx_fatura_cartao_mes_ano ON faturas (cartao_id, mes, ano);
CREATE INDEX idx_faturas_usuario_status ON faturas (usuario_id, status);

CREATE TABLE transacoes (
    id BIGSERIAL PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL,
    valor DECIMAL(19,2) NOT NULL,
    data DATE NOT NULL,
    conta_id BIGINT,
    cartao_id BIGINT,
    fatura_id BIGINT,
    categoria_id BIGINT,
    metodo_pagamento VARCHAR(50),
    tipo VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    usuario_id BIGINT NOT NULL,
    numero_parcela INTEGER,
    total_parcelas INTEGER,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transacoes_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_transacoes_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    CONSTRAINT fk_transacoes_conta FOREIGN KEY (conta_id) REFERENCES contas(id),
    CONSTRAINT fk_transacoes_cartao FOREIGN KEY (cartao_id) REFERENCES cartoes(id),
    CONSTRAINT fk_transacoes_fatura FOREIGN KEY (fatura_id) REFERENCES faturas(id)
);

CREATE INDEX idx_transacao_usuario_data ON transacoes (usuario_id, data);

