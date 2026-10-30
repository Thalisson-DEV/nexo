-- =====================================================================
-- V113: RH - Migração de Equipes para Domínios de Coordenador e Supervisor
-- =====================================================================

-- 1. Garantir que todo colaborador atualmente referenciado como id_coordenador em equipes possua registro em coordenadores
INSERT INTO coordenadores (id, id_colaborador, data_inicio, versao, data_criacao, ativo)
SELECT gen_random_uuid(), e.id_coordenador, CURRENT_DATE, 0, CURRENT_TIMESTAMP, TRUE
FROM equipes e
WHERE e.id_coordenador IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM coordenadores c WHERE c.id_colaborador = e.id_coordenador AND c.ativo = TRUE
  )
GROUP BY e.id_coordenador;

-- 2. Garantir que todo colaborador atualmente referenciado como id_supervisor em equipes possua registro em supervisores
-- vinculado ao respectivo coordenador da equipe
INSERT INTO supervisores (id, id_colaborador, id_coordenador, data_inicio, versao, data_criacao, ativo)
SELECT gen_random_uuid(), e.id_supervisor, c.id, CURRENT_DATE, 0, CURRENT_TIMESTAMP, TRUE
FROM equipes e
JOIN coordenadores c ON c.id_colaborador = e.id_coordenador AND c.ativo = TRUE
WHERE e.id_supervisor IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM supervisores s 
      WHERE s.id_colaborador = e.id_supervisor 
        AND s.id_coordenador = c.id 
        AND s.ativo = TRUE
  )
GROUP BY e.id_supervisor, c.id;

-- 3. Remover chaves estrangeiras antigas ANTES de atualizar os dados
ALTER TABLE equipes DROP CONSTRAINT IF EXISTS equipes_id_coordenador_fkey;
ALTER TABLE equipes DROP CONSTRAINT IF EXISTS equipes_id_supervisor_fkey;

-- 4. Atualizar equipes.id_coordenador para apontar para coordenadores(id)
UPDATE equipes e
SET id_coordenador = c.id
FROM coordenadores c
WHERE e.id_coordenador = c.id_colaborador;

-- 5. Atualizar equipes.id_supervisor para apontar para supervisores(id)
UPDATE equipes e
SET id_supervisor = s.id
FROM supervisores s
WHERE e.id_supervisor = s.id_colaborador
  AND s.id_coordenador = e.id_coordenador;

-- 6. Limpar eventuais referências residuais que não foram migradas para evitar erro ao recriar FK
UPDATE equipes
SET id_coordenador = NULL
WHERE id_coordenador IS NOT NULL
  AND id_coordenador NOT IN (SELECT id FROM coordenadores);

UPDATE equipes
SET id_supervisor = NULL
WHERE id_supervisor IS NOT NULL
  AND id_supervisor NOT IN (SELECT id FROM supervisores);

-- 7. Adicionar novas chaves estrangeiras apontando para coordenadores e supervisores
ALTER TABLE equipes
    ADD CONSTRAINT fk_equipes_coordenador
    FOREIGN KEY (id_coordenador) REFERENCES coordenadores(id);

ALTER TABLE equipes
    ADD CONSTRAINT fk_equipes_supervisor
    FOREIGN KEY (id_supervisor) REFERENCES supervisores(id);

-- 8. Criar índices para busca rápida
CREATE INDEX IF NOT EXISTS idx_equipes_coordenador ON equipes(id_coordenador);
CREATE INDEX IF NOT EXISTS idx_equipes_supervisor ON equipes(id_supervisor);
