-- V15: Criação de Indicadores Econômicos com Particionamento Mensal
CREATE TABLE indicador_economico (
    id BIGSERIAL NOT NULL,
    nome VARCHAR(50) NOT NULL, -- SELIC, CDI, IPCA, USD, EUR
    valor NUMERIC(19, 4) NOT NULL,
    variacao NUMERIC(10, 4),
    data_atualizacao DATE NOT NULL,
    provedor VARCHAR(50) NOT NULL, -- HG_BRASIL, AWESOME_API
    CONSTRAINT pk_indicador_economico PRIMARY KEY (id, data_atualizacao)
) PARTITION BY RANGE (data_atualizacao);

-- Partição Mensal Inicial (Abril 2026)
CREATE TABLE indicador_economico_2026_04 PARTITION OF indicador_economico
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');

CREATE INDEX idx_indicador_nome_data ON indicador_economico (nome, data_atualizacao);
