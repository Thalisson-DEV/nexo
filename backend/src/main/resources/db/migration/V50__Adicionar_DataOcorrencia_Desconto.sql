-- V50: Adicionar data de ocorrencia no desconto

ALTER TABLE desconto ADD COLUMN data_ocorrencia TIMESTAMP WITH TIME ZONE;

-- Preencher data_ocorrencia com data_criacao para os registros já existentes
UPDATE desconto SET data_ocorrencia = data_criacao WHERE data_ocorrencia IS NULL;

-- Tornar a coluna NOT NULL
ALTER TABLE desconto ALTER COLUMN data_ocorrencia SET NOT NULL;
