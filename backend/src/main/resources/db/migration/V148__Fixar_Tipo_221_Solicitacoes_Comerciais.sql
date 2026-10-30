-- V148: Fixar tipo_tecnico como '221' para solicitacoes comerciais de material (entregas para equipe)
UPDATE solicitacoes_material
SET tipo_tecnico = '221'
WHERE contexto = 'COMERCIAL' AND tipo_tecnico IS NULL;

ALTER TABLE solicitacoes_material
    ALTER COLUMN tipo_tecnico SET DEFAULT '221';
