-- Criação da tabela de Motivos de Rejeição
CREATE TABLE motivos_rejeicao (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255) NOT NULL,
    usuario_ultima_alteracao VARCHAR(255) NOT NULL,
    snapshot_anterior JSONB
);

-- Adicionando colunas de justificativa e motivo de rejeição na solicitação de material
ALTER TABLE solicitacoes_material ADD COLUMN id_motivo_rejeicao UUID REFERENCES motivos_rejeicao(id);
ALTER TABLE solicitacoes_material ADD COLUMN justificativa_rejeicao TEXT;
