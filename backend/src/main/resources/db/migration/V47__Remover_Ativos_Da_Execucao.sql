-- Migration para remover completamente as colunas de ativos (poste e posto) da tabela execucoes_comercial
ALTER TABLE execucoes_comercial DROP COLUMN IF EXISTS num_poste;
ALTER TABLE execucoes_comercial DROP COLUMN IF EXISTS num_posto;
