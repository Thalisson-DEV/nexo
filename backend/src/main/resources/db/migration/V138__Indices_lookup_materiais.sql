-- Índices das buscas server-side do autocomplete de materiais.
-- A migration do Flyway usa a criação transacional padrão dos índices.
CREATE INDEX IF NOT EXISTS idx_materiais_lookup_codigo_prefixo
    ON materiais (immutable_unaccent(lower(cod_material)) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_materiais_lookup_descricao_prefixo
    ON materiais (immutable_unaccent(lower(descricao)) text_pattern_ops);

CREATE INDEX IF NOT EXISTS idx_materiais_lookup_descricao_ordem
    ON materiais (immutable_unaccent(lower(descricao)), id);
