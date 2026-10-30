-- Classificacao usada apenas para derivar os tipos do fluxo tecnico.
-- Materiais existentes continuam comuns e o fluxo comercial permanece igual.
ALTER TABLE materiais
    ADD COLUMN classificacao_tecnica VARCHAR(20);

UPDATE materiais
SET classificacao_tecnica = 'COMUM'
WHERE classificacao_tecnica IS NULL;

ALTER TABLE materiais
    ALTER COLUMN classificacao_tecnica SET DEFAULT 'COMUM',
    ALTER COLUMN classificacao_tecnica SET NOT NULL;

ALTER TABLE materiais
    ADD CONSTRAINT ck_materiais_classificacao_tecnica
        CHECK (classificacao_tecnica IN ('COMUM', 'CONCRETO'));

CREATE INDEX idx_materiais_classificacao_tecnica
    ON materiais(classificacao_tecnica);
