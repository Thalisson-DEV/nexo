-- Book da obra: as fotos de cada ponto. Os itens pedidos ficam no modelo de checklist da ficha As Built.
CREATE TABLE evidencias_ponto_obra (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_ponto UUID NOT NULL REFERENCES pontos_planejamento (id),
    id_modelo UUID REFERENCES modelos_checklist_obra (id),
    id_item VARCHAR(100),
    titulo_item VARCHAR(150),
    legenda VARCHAR(500),
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    chave_storage VARCHAR(500) NOT NULL,
    chave_miniatura VARCHAR(500) NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    -- Foto de um item do modelo guarda o modelo e o título; sem item, é uma foto avulsa do ponto.
    CONSTRAINT ck_evidencias_ponto_item CHECK (
        (id_item IS NULL AND titulo_item IS NULL AND id_modelo IS NULL)
        OR (id_item IS NOT NULL AND titulo_item IS NOT NULL AND id_modelo IS NOT NULL)
    )
);

CREATE INDEX idx_evidencias_ponto_obra ON evidencias_ponto_obra (id_obra) WHERE ativo = TRUE;
CREATE INDEX idx_evidencias_ponto_ponto ON evidencias_ponto_obra (id_ponto) WHERE ativo = TRUE;
CREATE INDEX idx_evidencias_ponto_modelo ON evidencias_ponto_obra (id_modelo);
