-- V24: Adiciona Blind Index para Referência da Nota em Execuções Comerciais
-- Permite busca exata em campo criptografado (AES-256 GCM)

ALTER TABLE execucoes_comercial ADD COLUMN referencia_nota_hash VARCHAR(32);

-- Índice para busca rápida via Blind Index
CREATE INDEX idx_exec_comercial_ref_nota_hash ON execucoes_comercial(referencia_nota_hash);

COMMENT ON COLUMN execucoes_comercial.referencia_nota_hash IS 'Blind Index (Hash MD5 com Pepper) da referência da nota para busca exata.';
