-- =====================================================================
-- V111: Tornar Processo e Base Operacional obrigatórios na tabela de Obras
-- =====================================================================

-- 1. Limpar vínculos de dependência de obras sem processo ou base operacional
UPDATE obras SET id_obra_pai = NULL WHERE id_obra_pai IN (
    SELECT id FROM obras WHERE id_processo IS NULL OR id_base_operacional IS NULL
);

DELETE FROM obras_equipes WHERE id_obra IN (
    SELECT id FROM obras WHERE id_processo IS NULL OR id_base_operacional IS NULL
);

DELETE FROM anexos_obras WHERE id_obra IN (
    SELECT id FROM obras WHERE id_processo IS NULL OR id_base_operacional IS NULL
);

-- 2. Deletar obras inválidas/incompletas legadas
DELETE FROM obras WHERE id_processo IS NULL OR id_base_operacional IS NULL;

-- 3. Aplicar restrição NOT NULL
ALTER TABLE obras
    ALTER COLUMN id_processo SET NOT NULL,
    ALTER COLUMN id_base_operacional SET NOT NULL;
