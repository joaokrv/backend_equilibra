-- Parser CSV deixa de ser específico do Sicredi: enum CSV_SICREDI renomeado para CSV.
-- Alinha os registros existentes ao novo valor para não quebrar a deserialização da entidade.
UPDATE importacoes SET formato_detectado = 'CSV' WHERE formato_detectado = 'CSV_SICREDI';
