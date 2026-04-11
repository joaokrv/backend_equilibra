-- V10: Adicionar colunas de celular e preferência de moeda na tabela de usuários
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS celular VARCHAR(20);
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS moeda VARCHAR(3) DEFAULT 'BRL';

-- Comentários para auditoria
COMMENT ON COLUMN usuarios.celular IS 'Número de telefone/celular do usuário para contato e segurança';
COMMENT ON COLUMN usuarios.moeda IS 'Moeda preferencial para exibição de valores (BRL, USD, EUR)';
