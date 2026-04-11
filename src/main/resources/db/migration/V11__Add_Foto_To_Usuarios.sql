-- V11: Adicionar coluna de foto (binária) e verificação de e-mail na tabela de usuários
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS foto BYTEA;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS email_verificado BOOLEAN DEFAULT FALSE;

-- Comentários para auditoria
COMMENT ON COLUMN usuarios.foto IS 'Armazenamento binário da foto de perfil do usuário (com blindagem de magic bytes no service)';
COMMENT ON COLUMN usuarios.email_verificado IS 'Status de verificação de e-mail do usuário (OTP)';
