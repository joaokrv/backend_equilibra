-- V47: move a foto de perfil para tabela propria (usuario_foto).
-- Motivo: a coluna usuarios.foto (BYTEA) e EAGER e o UsuarioEntity e carregado a cada
-- request de autenticacao (loadUserByUsername) — lia ate 2MB do banco por request.
-- A foto agora fica isolada, carregada so quando pedida pelo endpoint dedicado.
CREATE TABLE IF NOT EXISTS usuario_foto (
    usuario_id    BIGINT       PRIMARY KEY REFERENCES usuarios(id) ON DELETE CASCADE,
    foto          BYTEA        NOT NULL,
    content_type  VARCHAR(40)  NOT NULL DEFAULT 'image/png',
    atualizado_em TIMESTAMP    NOT NULL DEFAULT now()
);

-- Preserva as fotos existentes antes de remover a coluna antiga.
INSERT INTO usuario_foto (usuario_id, foto)
    SELECT id, foto FROM usuarios WHERE foto IS NOT NULL
    ON CONFLICT (usuario_id) DO NOTHING;

ALTER TABLE usuarios DROP COLUMN IF EXISTS foto;
