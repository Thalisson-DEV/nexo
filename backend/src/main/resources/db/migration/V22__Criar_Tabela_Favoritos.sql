-- ---------------------------------------------------------
-- Feature: Favoritos do Usuário
-- Tabela para armazenar as rotas favoritas de cada usuário
-- ---------------------------------------------------------

CREATE TABLE IF NOT EXISTS favoritos (
    id UUID PRIMARY KEY,
    versao BIGINT,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    
    titulo VARCHAR(100) NOT NULL,
    rota VARCHAR(255) NOT NULL,
    icone VARCHAR(100),
    id_usuario UUID NOT NULL,

    CONSTRAINT fk_favoritos_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id)
);

CREATE INDEX idx_favoritos_usuario ON favoritos(id_usuario);
CREATE INDEX idx_favoritos_ativo ON favoritos(ativo);
