CREATE TABLE motivos_pendencia (
    id UUID PRIMARY KEY,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    usuario_criacao VARCHAR(255) NOT NULL,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE NOT NULL,
    usuario_ultima_alteracao VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB,
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao VARCHAR(255) NOT NULL
);

ALTER TABLE solicitacoes_material
    ADD COLUMN id_motivo_pendencia UUID REFERENCES motivos_pendencia(id),
    ADD COLUMN justificativa_pendencia VARCHAR(2000);

ALTER TABLE itens_solicitacao
    ADD COLUMN id_motivo_pendencia UUID REFERENCES motivos_pendencia(id),
    ADD COLUMN justificativa_pendencia VARCHAR(2000);
