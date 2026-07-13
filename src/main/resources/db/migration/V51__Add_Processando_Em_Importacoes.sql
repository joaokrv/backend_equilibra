-- Marca o instante em que uma sessão entrou em PROCESSANDO (claim atômico da confirmação).
-- Permite destravar sessões presas por crash no meio do lote após um limite de inatividade.
ALTER TABLE importacoes
    ADD COLUMN IF NOT EXISTS processando_em TIMESTAMP NULL;
