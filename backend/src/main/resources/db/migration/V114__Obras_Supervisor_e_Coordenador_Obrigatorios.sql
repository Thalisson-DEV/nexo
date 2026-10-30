-- =====================================================================
-- V114: Adicionar Supervisor e Coordenador obrigatórios na tabela de Obras
-- =====================================================================

-- 1. Adicionar colunas id_supervisor e id_coordenador
ALTER TABLE obras
    ADD COLUMN id_supervisor UUID,
    ADD COLUMN id_coordenador UUID;

-- 2. Se existirem obras cadastradas, vincular ao primeiro supervisor e coordenador ativos existentes
DO $$
DECLARE
    v_supervisor_id UUID;
    v_coordenador_id UUID;
BEGIN
    SELECT s.id, s.id_coordenador INTO v_supervisor_id, v_coordenador_id
    FROM supervisores s
    JOIN coordenadores c ON c.id = s.id_coordenador
    WHERE s.ativo = TRUE AND c.ativo = TRUE
    LIMIT 1;

    IF v_supervisor_id IS NOT NULL AND v_coordenador_id IS NOT NULL THEN
        UPDATE obras
        SET id_supervisor = v_supervisor_id,
            id_coordenador = v_coordenador_id
        WHERE id_supervisor IS NULL OR id_coordenador IS NULL;
    ELSE
        -- Se não houver supervisores/coordenadores cadastrados e houver obras órfãs, limpa dependências de obras
        UPDATE obras SET id_obra_pai = NULL WHERE id_obra_pai IN (
            SELECT id FROM obras WHERE id_supervisor IS NULL OR id_coordenador IS NULL
        );
        DELETE FROM obras_equipes WHERE id_obra IN (
            SELECT id FROM obras WHERE id_supervisor IS NULL OR id_coordenador IS NULL
        );
        DELETE FROM anexos_obras WHERE id_obra IN (
            SELECT id FROM obras WHERE id_supervisor IS NULL OR id_coordenador IS NULL
        );
        DELETE FROM obras WHERE id_supervisor IS NULL OR id_coordenador IS NULL;
    END IF;
END $$;

-- 3. Tornar as colunas obrigatórias (NOT NULL)
ALTER TABLE obras
    ALTER COLUMN id_supervisor SET NOT NULL,
    ALTER COLUMN id_coordenador SET NOT NULL;

-- 4. Criar chaves estrangeiras
ALTER TABLE obras
    ADD CONSTRAINT fk_obras_supervisor
    FOREIGN KEY (id_supervisor) REFERENCES supervisores(id);

ALTER TABLE obras
    ADD CONSTRAINT fk_obras_coordenador
    FOREIGN KEY (id_coordenador) REFERENCES coordenadores(id);

-- 5. Criar índices para otimização de consultas e joins
CREATE INDEX idx_obras_supervisor ON obras(id_supervisor);
CREATE INDEX idx_obras_coordenador ON obras(id_coordenador);
