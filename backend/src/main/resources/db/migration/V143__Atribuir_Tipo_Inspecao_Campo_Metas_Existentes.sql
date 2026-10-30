-- =====================================================================
-- V143: Atribuir tipo de inspeção 'Campo' para todas as metas existentes
--
-- Garante a atribuição do tipo de inspeção padrão 'Campo' para qualquer
-- meta de inspeção legada existente antes da parametrização de tipos.
-- =====================================================================

UPDATE metas_inspecao
SET id_tipo_inspecao = (SELECT id FROM tipos_inspecao WHERE nome_normalizado = 'campo')
WHERE id_tipo_inspecao IS NULL
   OR id_tipo_inspecao NOT IN (SELECT id FROM tipos_inspecao);
