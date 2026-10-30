-- V17: Criar tabelas para permissões dinâmicas
CREATE TABLE permissoes (
    id UUID PRIMARY KEY,
    versao BIGINT NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB,
    nome VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE perfil_permissoes (
    id_perfil UUID NOT NULL,
    id_permissao UUID NOT NULL,
    PRIMARY KEY (id_perfil, id_permissao),
    CONSTRAINT fk_perfil FOREIGN KEY (id_perfil) REFERENCES perfis_acesso(id),
    CONSTRAINT fk_permissao FOREIGN KEY (id_permissao) REFERENCES permissoes(id)
);

CREATE INDEX idx_permissoes_nome ON permissoes(nome);
