-- Extensoes aditivas para ambientes em que a V95 ja foi aplicada.
-- Migrations publicadas sao imutaveis; novos campos entram sempre em nova versao.

ALTER TABLE reunioes_governanca_sst
    ADD COLUMN duracao_minutos INTEGER;

UPDATE reunioes_governanca_sst
SET duracao_minutos = 60
WHERE duracao_minutos IS NULL;

ALTER TABLE reunioes_governanca_sst
    ALTER COLUMN duracao_minutos SET NOT NULL;

ALTER TABLE reunioes_governanca_sst
    ADD CONSTRAINT ck_reuniao_governanca_duracao
        CHECK (duracao_minutos BETWEEN 1 AND 1440);
