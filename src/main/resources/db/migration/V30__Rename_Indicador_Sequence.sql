-- V30: Renomeia a sequence da tabela indicador_economico
-- Na V28, criamos indicador_economico_v2 usando BIGSERIAL, o que gerou a sequence indicador_economico_v2_id_seq.
-- Quando renomeamos a tabela para indicador_economico, a sequence manteve o nome v2.
-- O Hibernate (ddl-auto=validate) espera que a sequence se chame indicador_economico_id_seq.

ALTER SEQUENCE indicador_economico_v2_id_seq RENAME TO indicador_economico_id_seq;
