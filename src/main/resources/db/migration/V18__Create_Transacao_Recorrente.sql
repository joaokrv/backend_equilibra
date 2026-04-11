-- V18: Cria tabelas para transações recorrentes (receitas/despesas fixas).
-- transacoes_recorrentes: template da recorrência
-- recorrencias_canceladas: meses específicos cancelados pelo usuário
-- Adiciona recorrente_id em transacoes para rastrear origem

CREATE TABLE transacoes_recorrentes (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    valor DECIMAL(19,2) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    metodo_pagamento VARCHAR(50),
    conta_id BIGINT NOT NULL,
    cartao_id BIGINT,
    categoria_id BIGINT,
    dia_lancamento INTEGER NOT NULL CHECK (dia_lancamento BETWEEN 1 AND 31),
    data_inicio DATE NOT NULL DEFAULT CURRENT_DATE,
    data_fim DATE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recorrente_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_recorrente_conta FOREIGN KEY (conta_id) REFERENCES contas(id),
    CONSTRAINT fk_recorrente_cartao FOREIGN KEY (cartao_id) REFERENCES cartoes(id),
    CONSTRAINT fk_recorrente_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE INDEX idx_recorrente_usuario ON transacoes_recorrentes (usuario_id);
CREATE INDEX idx_recorrente_dia ON transacoes_recorrentes (dia_lancamento);

CREATE TABLE recorrencias_canceladas (
    id BIGSERIAL PRIMARY KEY,
    recorrente_id BIGINT NOT NULL,
    ano INTEGER NOT NULL,
    mes INTEGER NOT NULL,
    CONSTRAINT fk_cancelada_recorrente FOREIGN KEY (recorrente_id) REFERENCES transacoes_recorrentes(id),
    CONSTRAINT uk_cancelada_recorrente_mes UNIQUE (recorrente_id, ano, mes)
);

-- Vincula transação gerada à recorrência-mãe (nullable = transação manual)
ALTER TABLE transacoes ADD COLUMN recorrente_id BIGINT REFERENCES transacoes_recorrentes(id);

-- Flag para categorias padrão do sistema
ALTER TABLE categorias ADD COLUMN is_padrao BOOLEAN NOT NULL DEFAULT FALSE;
