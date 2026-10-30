-- Relacionamentos canonicos e nao hierarquicos entre projetos independentes.
CREATE TABLE obras_relacionamentos (
    id UUID PRIMARY KEY,
    id_projeto_a UUID NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
    id_projeto_b UUID NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
    tipo VARCHAR(20) NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_obras_relacionamentos_projetos_distintos
        CHECK (id_projeto_a <> id_projeto_b),
    CONSTRAINT ck_obras_relacionamentos_par_canonico
        CHECK (id_projeto_a::text < id_projeto_b::text),
    CONSTRAINT ck_obras_relacionamentos_tipo
        CHECK (tipo IN ('KIT', 'MEDIDOR')),
    CONSTRAINT uq_obras_relacionamentos_par
        UNIQUE (id_projeto_a, id_projeto_b)
);

CREATE INDEX idx_obras_relacionamentos_projeto_a
    ON obras_relacionamentos(id_projeto_a);
CREATE INDEX idx_obras_relacionamentos_projeto_b
    ON obras_relacionamentos(id_projeto_b);
CREATE INDEX idx_obras_relacionamentos_tipo
    ON obras_relacionamentos(tipo);

-- Copia os vinculos legados. As colunas antigas permanecem durante a janela
-- de compatibilidade, mas a tabela canonica passa a ser a fonte de verdade.
INSERT INTO obras_relacionamentos (
    id,
    id_projeto_a,
    id_projeto_b,
    tipo,
    versao,
    data_criacao,
    usuario_criacao,
    ativo
)
SELECT
    gen_random_uuid(),
    CASE
        WHEN obra.id::text < obra.id_obra_pai::text THEN obra.id
        ELSE obra.id_obra_pai
    END,
    CASE
        WHEN obra.id::text < obra.id_obra_pai::text THEN obra.id_obra_pai
        ELSE obra.id
    END,
    obra.tipo_vinculo_pai,
    0,
    CURRENT_TIMESTAMP,
    'MIGRACAO_V116',
    TRUE
FROM obras obra
WHERE obra.id_obra_pai IS NOT NULL
  AND obra.id <> obra.id_obra_pai
  AND obra.tipo_vinculo_pai IN ('KIT', 'MEDIDOR')
ON CONFLICT (id_projeto_a, id_projeto_b) DO NOTHING;
