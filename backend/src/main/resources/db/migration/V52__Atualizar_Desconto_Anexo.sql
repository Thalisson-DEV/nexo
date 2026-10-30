ALTER TABLE desconto_anexo
ADD COLUMN content_type VARCHAR(100) NOT NULL DEFAULT 'application/octet-stream',
ADD COLUMN tamanho_bytes BIGINT NOT NULL DEFAULT 0;

-- Remover o default depois de preencher
ALTER TABLE desconto_anexo
ALTER COLUMN content_type DROP DEFAULT,
ALTER COLUMN tamanho_bytes DROP DEFAULT;
