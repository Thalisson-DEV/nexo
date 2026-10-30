CREATE INDEX IF NOT EXISTS idx_estoques_lookup_nome_ativo
    ON estoques (immutable_unaccent(LOWER(nome_identificador)), id)
    WHERE ativo = TRUE;

CREATE INDEX IF NOT EXISTS idx_estoques_lookup_tipo_ativo
    ON estoques (id_tipo_estoque)
    WHERE ativo = TRUE;

