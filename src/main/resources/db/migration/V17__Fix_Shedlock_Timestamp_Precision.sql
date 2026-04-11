-- V17: Normaliza precisão de timestamps da tabela shedlock.
-- V5 criou com TIMESTAMP(3) e V13 recriou com TIMESTAMP (sem precisão).
-- ShedLock requer milissegundos para funcionar corretamente.
ALTER TABLE shedlock ALTER COLUMN lock_until TYPE TIMESTAMP(3);
ALTER TABLE shedlock ALTER COLUMN locked_at TYPE TIMESTAMP(3);
