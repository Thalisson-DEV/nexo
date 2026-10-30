-- Conversas do assistente analitico. O modelo nunca recebe acesso a estas tabelas.
CREATE TABLE assistente_conversa (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    titulo VARCHAR(160) NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_assistente_conversa_usuario_data
    ON assistente_conversa (usuario_id, data_criacao DESC)
    WHERE ativo = TRUE;

CREATE TABLE assistente_mensagem (
    id UUID PRIMARY KEY,
    conversa_id UUID NOT NULL REFERENCES assistente_conversa(id),
    client_message_id UUID NOT NULL,
    papel VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    conteudo TEXT NOT NULL,
    evidencias_json TEXT,
    acoes_json TEXT,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_assistente_mensagem_cliente_papel
        UNIQUE (conversa_id, client_message_id, papel),
    CONSTRAINT ck_assistente_mensagem_papel
        CHECK (papel IN ('USUARIO', 'ASSISTENTE'))
);

CREATE INDEX idx_assistente_mensagem_conversa_data
    ON assistente_mensagem (conversa_id, data_criacao);

CREATE TABLE assistente_execucao_ferramenta (
    id UUID PRIMARY KEY,
    mensagem_id UUID NOT NULL REFERENCES assistente_mensagem(id),
    dataset VARCHAR(120) NOT NULL,
    dataset_versao VARCHAR(30) NOT NULL,
    sucesso BOOLEAN NOT NULL,
    duracao_ms BIGINT NOT NULL,
    evidencia_json TEXT,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assistente_execucao_mensagem
    ON assistente_execucao_ferramenta (mensagem_id, data_criacao);

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'assistente:chat:use')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
JOIN permissoes permissao ON permissao.nome = 'assistente:chat:use'
WHERE UPPER(TRIM(perfil.nome)) = 'ADMINISTRADOR'
ON CONFLICT DO NOTHING;
