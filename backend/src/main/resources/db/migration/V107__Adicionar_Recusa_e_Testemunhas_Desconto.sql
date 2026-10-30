-- V107: Adicionar suporte ao direito de recusa e testemunhas no processo de desconto
ALTER TABLE desconto
ADD COLUMN recusa_colaborador BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE desconto_testemunha (
    id UUID PRIMARY KEY,
    desconto_id UUID NOT NULL REFERENCES desconto(id) ON DELETE CASCADE,
    nome VARCHAR(255) NOT NULL,
    cpf VARCHAR(255) NOT NULL,
    cpf_hash VARCHAR(255),
    
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE INDEX idx_desconto_testemunha_desconto ON desconto_testemunha(desconto_id);
CREATE INDEX idx_desconto_testemunha_cpf_hash ON desconto_testemunha(cpf_hash);
