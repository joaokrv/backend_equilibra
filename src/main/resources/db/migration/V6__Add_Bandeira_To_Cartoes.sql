-- Migration para adicionar a bandeira do cartão para fins estéticos no Frontend
ALTER TABLE cartoes ADD COLUMN bandeira VARCHAR(50) NOT NULL DEFAULT 'OUTROS';
