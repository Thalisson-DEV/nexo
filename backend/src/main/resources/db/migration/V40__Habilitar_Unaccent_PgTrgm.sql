-- Habilita busca textual case/accent-insensitive (busca rápida uniforme "buscaGlobal").
-- unaccent: remove acentos | pg_trgm: índices GIN para LIKE '%termo%'.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- A função unaccent(text) é STABLE (depende da configuração do dicionário em tempo de execução),
-- portanto não pode ser usada diretamente em índices funcionais. Este wrapper fixa o dicionário
-- e é declarado IMMUTABLE, garantindo a MESMA expressão entre a query e o índice gin_trgm.
CREATE OR REPLACE FUNCTION immutable_unaccent(text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE PARALLEL SAFE STRICT AS
$$
SELECT public.unaccent('public.unaccent', $1)
$$;
