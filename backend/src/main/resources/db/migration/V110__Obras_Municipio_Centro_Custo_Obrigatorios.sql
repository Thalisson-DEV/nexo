-- =====================================================================
-- V110: Tornar Município e Centro de Custo obrigatórios na tabela de Obras
-- =====================================================================

-- 1. Limpar vínculos de dependência de obras sem município ou centro de custo
UPDATE obras SET id_obra_pai = NULL WHERE id_obra_pai IN (
    SELECT id FROM obras WHERE id_municipio IS NULL OR id_centro_custo IS NULL
);

DELETE FROM obras_equipes WHERE id_obra IN (
    SELECT id FROM obras WHERE id_municipio IS NULL OR id_centro_custo IS NULL
);

DELETE FROM anexos_obras WHERE id_obra IN (
    SELECT id FROM obras WHERE id_municipio IS NULL OR id_centro_custo IS NULL
);

-- 2. Deletar obras inválidas/incompletas legadas
DELETE FROM obras WHERE id_municipio IS NULL OR id_centro_custo IS NULL;

-- 3. Aplicar restrição NOT NULL
ALTER TABLE obras
    ALTER COLUMN id_municipio SET NOT NULL,
    ALTER COLUMN id_centro_custo SET NOT NULL;
