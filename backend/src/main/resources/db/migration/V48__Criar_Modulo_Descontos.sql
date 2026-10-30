-- V48: Criar módulo de descontos (Tipos de Equipamento, Motivos e Descontos)
-- Tabelas com suporte completo a BaseEntity (Auditoria, UUID, JSONB Snapshot)

CREATE TABLE tipo_equipamento (
    id UUID PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL,
    
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE TABLE motivo_desconto (
    id UUID PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL,
    teto_parcelas INTEGER NOT NULL,
    
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE TABLE desconto (
    id UUID PRIMARY KEY,
    colaborador_id UUID NOT NULL,
    motivo_desconto_id UUID NOT NULL REFERENCES motivo_desconto(id),
    observacoes TEXT,
    quantidade_parcelas INTEGER NOT NULL,
    usuario_solicitante_id UUID NOT NULL,
    usuario_validador_id UUID,
    usuario_baixa_id UUID,
    status VARCHAR(50) NOT NULL,
    data_validacao TIMESTAMP WITH TIME ZONE,
    data_baixa TIMESTAMP WITH TIME ZONE,
    
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE TABLE desconto_item (
    id UUID PRIMARY KEY,
    desconto_id UUID NOT NULL REFERENCES desconto(id),
    tipo_equipamento_id UUID NOT NULL REFERENCES tipo_equipamento(id),
    descricao_material VARCHAR(255) NOT NULL,
    valor NUMERIC(12, 2) NOT NULL,
    
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

CREATE TABLE desconto_anexo (
    id UUID PRIMARY KEY,
    desconto_id UUID NOT NULL REFERENCES desconto(id),
    nome_arquivo VARCHAR(255) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    snapshot_anterior JSONB
);

-- Índices de performance
CREATE INDEX idx_desconto_colaborador ON desconto(colaborador_id);
CREATE INDEX idx_desconto_status ON desconto(status);
CREATE INDEX idx_desconto_item_desconto ON desconto_item(desconto_id);
CREATE INDEX idx_desconto_anexo_desconto ON desconto_anexo(desconto_id);

-- Inserindo permissões para o módulo IAM (padrão do sistema)
INSERT INTO permissoes (id, versao, data_criacao, ativo, nome) VALUES 
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:tipo-equipamento:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:tipo-equipamento:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:motivo:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:motivo:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:solicitar'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:editar'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:anexar'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:validar'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:baixar'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'desconto:read');

-- Vincular as permissões recém-criadas ao perfil ADMINISTRADOR
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pa.id, p.id
FROM perfis_acesso pa, permissoes p
WHERE pa.nome = 'ADMINISTRADOR'
  AND p.nome LIKE 'desconto:%';
