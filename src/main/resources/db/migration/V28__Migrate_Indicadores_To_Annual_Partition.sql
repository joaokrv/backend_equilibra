-- V28: Migração de Indicadores Econômicos e Patrimônio Histórico para Particionamento Anual
-- Esta migration simplifica a arquitetura e resolve os erros de Cold Start no dia 1º de cada mês.

-- 1. Criação da nova tabela de indicadores com particionamento anual
CREATE TABLE indicador_economico_v2 (
    id BIGSERIAL NOT NULL,
    nome VARCHAR(50) NOT NULL,
    valor NUMERIC(19, 4) NOT NULL,
    variacao NUMERIC(10, 4),
    data_atualizacao DATE NOT NULL,
    provedor VARCHAR(50) NOT NULL,
    CONSTRAINT pk_indicador_economico_v2 PRIMARY KEY (id, data_atualizacao)
) PARTITION BY RANGE (data_atualizacao);

-- 2. Criação da partição anual para o ano atual (2026) e próximo (2027)
CREATE TABLE indicador_economico_2026 PARTITION OF indicador_economico_v2
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE TABLE indicador_economico_2027 PARTITION OF indicador_economico_v2
    FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');

-- 3. Migração segura dos dados existentes (se houver) para a nova estrutura
INSERT INTO indicador_economico_v2 (id, nome, valor, variacao, data_atualizacao, provedor)
SELECT id, nome, valor, variacao, data_atualizacao, provedor FROM indicador_economico;

-- 4. Remoção da estrutura antiga (mensal) e renomeação da nova
DROP TABLE indicador_economico CASCADE;
ALTER TABLE indicador_economico_v2 RENAME TO indicador_economico;
ALTER INDEX pk_indicador_economico_v2 RENAME TO pk_indicador_economico;

-- 5. Recriação do índice
CREATE INDEX idx_indicador_nome_data ON indicador_economico (nome, data_atualizacao);

-- Nota: patrimonio_historico já possui partição anual (patrimonio_historico_2026) desde a V14.
-- Apenas adicionaremos a partição de 2027 preventivamente.
CREATE TABLE IF NOT EXISTS patrimonio_historico_2027 PARTITION OF patrimonio_historico
    FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');
