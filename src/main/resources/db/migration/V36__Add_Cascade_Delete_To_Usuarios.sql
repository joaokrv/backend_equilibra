-- V36: Adiciona ON DELETE CASCADE nas FKs filhas de usuarios para viabilizar hard delete.
-- Necessário para o fluxo de exclusão de conta (RFC-Exclusao-Desativacao-Conta).
--
-- Tabelas já com CASCADE:
--   patrimonio_historico → fk_patrimonio_usuario (ON DELETE CASCADE desde V14)
--
-- Tabelas sem FK para usuarios (link por email — limpeza feita no service):
--   tokens_recuperacao_senha, codigos_verificacao, usuarios_pendentes

-- 1. categorias
ALTER TABLE categorias DROP CONSTRAINT fk_categorias_usuario;
ALTER TABLE categorias ADD CONSTRAINT fk_categorias_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 2. contas
ALTER TABLE contas DROP CONSTRAINT fk_contas_usuario;
ALTER TABLE contas ADD CONSTRAINT fk_contas_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 3. cartoes
ALTER TABLE cartoes DROP CONSTRAINT fk_cartoes_usuario;
ALTER TABLE cartoes ADD CONSTRAINT fk_cartoes_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 4. investimentos
ALTER TABLE investimentos DROP CONSTRAINT fk_investimentos_usuario;
ALTER TABLE investimentos ADD CONSTRAINT fk_investimentos_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 5. faturas
ALTER TABLE faturas DROP CONSTRAINT fk_faturas_usuario;
ALTER TABLE faturas ADD CONSTRAINT fk_faturas_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 6. transacoes
ALTER TABLE transacoes DROP CONSTRAINT fk_transacoes_usuario;
ALTER TABLE transacoes ADD CONSTRAINT fk_transacoes_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 7. transacoes_recorrentes
ALTER TABLE transacoes_recorrentes DROP CONSTRAINT fk_recorrente_usuario;
ALTER TABLE transacoes_recorrentes ADD CONSTRAINT fk_recorrente_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 8. solicitacoes_alteracao_email (constraint gerada automaticamente pelo PostgreSQL)
ALTER TABLE solicitacoes_alteracao_email
    DROP CONSTRAINT IF EXISTS solicitacoes_alteracao_email_usuario_id_fkey;
ALTER TABLE solicitacoes_alteracao_email ADD CONSTRAINT fk_solic_email_usuario
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE;

-- 9. recorrencias_canceladas → transacoes_recorrentes (cadeia de CASCADE)
--    Sem isso, DELETE de transacoes_recorrentes (via CASCADE de usuarios) falha.
ALTER TABLE recorrencias_canceladas DROP CONSTRAINT fk_cancelada_recorrente;
ALTER TABLE recorrencias_canceladas ADD CONSTRAINT fk_cancelada_recorrente
    FOREIGN KEY (recorrente_id) REFERENCES transacoes_recorrentes(id) ON DELETE CASCADE;

-- 10. transacoes.recorrente_id → transacoes_recorrentes (SET NULL — transação mantida sem vínculo)
--     Constraint é inline REFERENCES sem nome; PostgreSQL gera transacoes_recorrente_id_fkey.
ALTER TABLE transacoes
    DROP CONSTRAINT IF EXISTS transacoes_recorrente_id_fkey;
ALTER TABLE transacoes ADD CONSTRAINT fk_transacao_recorrente_id
    FOREIGN KEY (recorrente_id) REFERENCES transacoes_recorrentes(id) ON DELETE SET NULL;
