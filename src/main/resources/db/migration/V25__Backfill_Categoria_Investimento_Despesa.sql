-- Garante que todos os usuarios tenham a categoria padrao "Investimento" como DESPESA.

-- 1) Se a categoria ativa ja existir, garante flag de padrao.
UPDATE categorias c
SET is_padrao = true,
    data_atualizacao = NOW()
WHERE c.ativo = true
  AND c.nome = 'Investimento'
  AND c.tipo = 'DESPESA'
  AND c.is_padrao = false;

-- 2) Cria a categoria para usuarios que ainda nao possuem uma categoria ativa equivalente.
INSERT INTO categorias (nome, tipo, usuario_id, ativo, is_padrao, data_criacao, data_atualizacao)
SELECT 'Investimento', 'DESPESA', u.id, true, true, NOW(), NOW()
FROM usuarios u
WHERE NOT EXISTS (
    SELECT 1
    FROM categorias c
    WHERE c.usuario_id = u.id
      AND c.nome = 'Investimento'
      AND c.tipo = 'DESPESA'
      AND c.ativo = true
);
