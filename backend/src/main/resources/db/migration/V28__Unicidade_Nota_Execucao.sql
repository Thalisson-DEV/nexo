-- V29: Garantir unicidade da Referência da Nota em Execuções Comerciais via Blind Index

-- Como referencia_nota é criptografado (AES GCM), usamos a coluna de hash para garantir unicidade
-- Removemos índices anteriores para evitar conflitos (se houver) e recriamos como UNIQUE
DROP INDEX IF EXISTS idx_exec_comercial_ref_nota_hash;

CREATE UNIQUE INDEX idx_exec_comercial_ref_nota_hash_unique ON execucoes_comercial(referencia_nota_hash) 
WHERE (referencia_nota_hash IS NOT NULL AND ativo = TRUE);

COMMENT ON INDEX idx_exec_comercial_ref_nota_hash_unique IS 'Garante que não existam notas duplicadas ativas no sistema via Blind Index.';
