-- Coluna de busca em texto puro para o Colaborador (nome/CPF são cifrados em repouso, então
-- não é possível fazer LIKE parcial sobre eles). Armazena o nome normalizado (lower + sem acento)
-- permitindo a busca rápida parcial por nome. O CPF permanece cifrado/exato (blind index).
ALTER TABLE colaboradores
    ADD COLUMN IF NOT EXISTS nome_busca text;

-- Índice trigram para acelerar LIKE '%termo%'. A expressão deve ser idêntica à usada na query
-- (SpecificationUtils -> immutable_unaccent(lower(nome_busca))).
CREATE INDEX IF NOT EXISTS idx_colaboradores_nome_busca_trgm
    ON colaboradores USING gin (immutable_unaccent(lower(nome_busca)) gin_trgm_ops);

-- Backfill das linhas existentes é feito no startup pela aplicação (NomeBuscaBackfillRunner),
-- pois o nome_completo está cifrado e não pode ser descriptografado em SQL puro.
