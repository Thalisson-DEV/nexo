-- V34: Remover unicidade da Referencia da Nota em Execucoes Comerciais
-- Permite que uma mesma nota tenha multiplas execucoes

DROP INDEX IF EXISTS idx_exec_comercial_ref_nota_hash_unique;

-- Recriando como um indice comum para otimizar as buscas via blind index
CREATE INDEX idx_exec_comercial_ref_nota_hash ON execucoes_comercial(referencia_nota_hash) 
WHERE (referencia_nota_hash IS NOT NULL AND ativo = TRUE);

COMMENT ON INDEX idx_exec_comercial_ref_nota_hash IS 'Indice para busca rapida de notas no sistema via Blind Index.';
