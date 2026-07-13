-- Aportes/resgates importados com "atualizarValor=false" registram só o histórico, sem mexer
-- no valorAtual do investimento. A flag precisa ser persistida para que a exclusão/desfazer
-- só reverta o valorAtual quando ele foi de fato ajustado na criação.
-- DEFAULT TRUE: todas as movimentações manuais pré-existentes sempre ajustaram o valor.
ALTER TABLE movimentacao_investimento
    ADD COLUMN IF NOT EXISTS ajustou_valor BOOLEAN NOT NULL DEFAULT TRUE;
