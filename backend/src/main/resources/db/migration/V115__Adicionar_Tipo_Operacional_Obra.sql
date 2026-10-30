-- Classificacao operacional usada pelo fluxo tecnico de materiais.
-- A alteracao e aditiva e preserva os identificadores e vinculos existentes.
ALTER TABLE obras
    ADD COLUMN tipo_obra VARCHAR(20);

UPDATE obras
SET tipo_obra = 'OBRA'
WHERE tipo_obra IS NULL;

ALTER TABLE obras
    ALTER COLUMN tipo_obra SET DEFAULT 'OBRA',
    ALTER COLUMN tipo_obra SET NOT NULL;

ALTER TABLE obras
    ADD CONSTRAINT ck_obras_tipo_obra
        CHECK (tipo_obra IN ('OBRA', 'CORRETIVA', 'PREVENTIVA', 'LPT'));

CREATE INDEX idx_obras_tipo_obra ON obras(tipo_obra);
