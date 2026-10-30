CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Auditoria Global
CREATE TABLE logs_sistema (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tabela_afetada VARCHAR(50) NOT NULL,
    registro_id UUID NOT NULL,
    usuario_id UUID,
    operacao VARCHAR(20) NOT NULL, -- INSERT, UPDATE, DELETE, REVERSAL, LOGIN
    dados_anteriores JSONB,
    dados_novos JSONB,
    data_hora TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_logs_registro ON logs_sistema(registro_id);

CREATE TABLE perfis_acesso (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(50) NOT NULL UNIQUE,
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE usuarios (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    login VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) UNIQUE,
    telefone VARCHAR(20),
    senha_hash VARCHAR(255) NOT NULL, -- 🔒
    id_perfil UUID REFERENCES perfis_acesso(id),
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

COMMENT ON COLUMN usuarios.senha_hash IS '🔒 DADO SENSÍVEL';