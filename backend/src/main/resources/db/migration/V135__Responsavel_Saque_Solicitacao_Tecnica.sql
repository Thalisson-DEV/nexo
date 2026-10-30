-- Adiciona suporte a identificação do colaborador responsável pelo saque nas solicitações e operações técnicas

ALTER TABLE solicitacoes_material
    ADD COLUMN IF NOT EXISTS id_colaborador_saque UUID REFERENCES colaboradores(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS saque_fora_equipe BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE operacoes_solicitacao_material
    ADD COLUMN IF NOT EXISTS id_colaborador_saque UUID REFERENCES colaboradores(id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS saque_fora_equipe BOOLEAN DEFAULT FALSE NOT NULL;

CREATE INDEX IF NOT EXISTS idx_solicitacoes_material_colaborador_saque
    ON solicitacoes_material(id_colaborador_saque)
    WHERE id_colaborador_saque IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_operacoes_solicitacao_colaborador_saque
    ON operacoes_solicitacao_material(id_colaborador_saque)
    WHERE id_colaborador_saque IS NOT NULL;
