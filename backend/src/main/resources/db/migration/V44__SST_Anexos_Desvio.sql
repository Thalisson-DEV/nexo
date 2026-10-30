-- V44: Anexos (evidências) de desvios SST.
-- Cada desvio pode ter zero ou mais evidências (fotos ou documentos PDF/Word), tanto
-- da não-conformidade (contexto=DESVIO) quanto da ação corretiva (contexto=ACAO_CORRETIVA).
-- O conteúdo binário fica no Cloudflare R2; aqui guardam-se apenas os metadados e a chave do objeto.
CREATE TABLE anexos_desvio (
    id UUID PRIMARY KEY,
    id_desvio UUID NOT NULL REFERENCES desvios_seguranca(id),
    contexto VARCHAR(20) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    object_key VARCHAR(1024) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_anexo_object_key UNIQUE (object_key)
);

CREATE INDEX idx_anexos_desvio ON anexos_desvio(id_desvio);
