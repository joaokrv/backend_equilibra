-- V48: Adiciona ON DELETE CASCADE nas FKs criadas após V36 que referenciam usuarios(id).
-- Causa: V39 (notificacao_fatura) e V41 (movimentacao_investimento) foram criadas depois do
--        V36 (Add_Cascade_Delete_To_Usuarios) e ficaram sem CASCADE, bloqueando o hard delete
--        de usuário com DataIntegrityViolationException.
--
-- DROP CONSTRAINT IF EXISTS em todos os ALTERs: protege contra divergência de nomes de
-- constraints auto-geradas pelo PostgreSQL entre ambientes.

-- 1. notificacao_fatura.usuario_id → CASCADE (bloqueio crítico)
ALTER TABLE notificacao_fatura DROP CONSTRAINT IF EXISTS fk_notificacao_usuario;
ALTER TABLE notificacao_fatura ADD CONSTRAINT fk_notificacao_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 2. notificacao_fatura.fatura_id → CASCADE
--    Sem isso, se faturas forem deletadas (via cascade de usuario) antes de notificacao_fatura
--    ser deletada (via cascade de usuario_id), o PostgreSQL violaria esta FK.
ALTER TABLE notificacao_fatura DROP CONSTRAINT IF EXISTS fk_notificacao_fatura;
ALTER TABLE notificacao_fatura ADD CONSTRAINT fk_notificacao_fatura
    FOREIGN KEY (fatura_id) REFERENCES faturas(id) ON DELETE CASCADE;

-- 3. movimentacao_investimento.usuario_id → CASCADE (bloqueio crítico)
ALTER TABLE movimentacao_investimento
    DROP CONSTRAINT IF EXISTS movimentacao_investimento_usuario_id_fkey;
ALTER TABLE movimentacao_investimento ADD CONSTRAINT fk_mov_inv_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 4. movimentacao_investimento.investimento_id → CASCADE (robustez)
--    Garante que movimentações sejam removidas ao deletar um investimento individualmente.
ALTER TABLE movimentacao_investimento
    DROP CONSTRAINT IF EXISTS movimentacao_investimento_investimento_id_fkey;
ALTER TABLE movimentacao_investimento ADD CONSTRAINT fk_mov_inv_investimento
    FOREIGN KEY (investimento_id) REFERENCES investimentos(id) ON DELETE CASCADE;

-- 5. movimentacao_investimento.conta_id → SET NULL (coluna nullable; histórico preservado)
ALTER TABLE movimentacao_investimento
    DROP CONSTRAINT IF EXISTS movimentacao_investimento_conta_id_fkey;
ALTER TABLE movimentacao_investimento ADD CONSTRAINT fk_mov_inv_conta
    FOREIGN KEY (conta_id) REFERENCES contas(id) ON DELETE SET NULL;

-- 6. movimentacao_investimento.transacao_id → SET NULL (coluna nullable; histórico preservado)
ALTER TABLE movimentacao_investimento
    DROP CONSTRAINT IF EXISTS movimentacao_investimento_transacao_id_fkey;
ALTER TABLE movimentacao_investimento ADD CONSTRAINT fk_mov_inv_transacao
    FOREIGN KEY (transacao_id) REFERENCES transacoes(id) ON DELETE SET NULL;
