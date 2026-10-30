-- =====================================================================
-- V194: Conexões de IA (servidor MCP do Nexo)
--
-- Cada pessoa pode conectar o Nexo à IA que já usa (Claude, ChatGPT,
-- Cursor...) pelo protocolo MCP. A IA enxerga só o que a pessoa enxerga:
-- as ferramentas do Copiloto filtradas pelas permissões do perfil.
--
-- * assistente_conexao_cliente: aplicativos registrados pelo cadastro
--   dinâmico do OAuth (RFC 7591). Clientes públicos, sem segredo.
-- * assistente_conexao: a conexão autorizada pela pessoa (pelo login,
--   OAuth, ou por chave de acesso), com o que pode fazer e o último uso.
--   Revogar a conexão invalida todas as credenciais dela.
-- * assistente_conexao_credencial: tokens de acesso, de renovação e
--   chaves. Só o hash SHA-256 é guardado.
-- * assistente_conexao_codigo: códigos de autorização (uso único, PKCE).
--
-- As chamadas da IA ficam numa conversa técnica (origem MCP), que não
-- aparece no histórico do Copiloto: guarda os resultados (r1, r2...)
-- e a auditoria de cada ferramenta executada.
-- =====================================================================

CREATE TABLE assistente_conexao_cliente (
    id UUID PRIMARY KEY,
    client_id VARCHAR(80) NOT NULL,
    nome VARCHAR(120) NOT NULL,
    redirect_uris TEXT NOT NULL,
    site VARCHAR(300),
    ip_registro VARCHAR(64),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_assistente_conexao_cliente UNIQUE (client_id)
);

CREATE TABLE assistente_conexao (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    nome VARCHAR(120) NOT NULL,
    client_id VARCHAR(80),
    permite_escrita BOOLEAN NOT NULL DEFAULT FALSE,
    prefixo_chave VARCHAR(24),
    expira_em TIMESTAMP WITH TIME ZONE,
    revogada_em TIMESTAMP WITH TIME ZONE,
    revogada_por UUID,
    ultimo_uso_em TIMESTAMP WITH TIME ZONE,
    ultimo_ip VARCHAR(64),
    aplicativo VARCHAR(120),
    total_chamadas BIGINT NOT NULL DEFAULT 0,
    conversa_id UUID REFERENCES assistente_conversa (id),
    mensagem_id UUID REFERENCES assistente_mensagem (id),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_assistente_conexao_tipo CHECK (tipo IN ('OAUTH', 'CHAVE'))
);

CREATE INDEX idx_assistente_conexao_usuario ON assistente_conexao (usuario_id, data_criacao DESC) WHERE ativo = TRUE;

CREATE TABLE assistente_conexao_credencial (
    id UUID PRIMARY KEY,
    conexao_id UUID NOT NULL REFERENCES assistente_conexao (id),
    tipo VARCHAR(10) NOT NULL,
    hash VARCHAR(64) NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE,
    usada_em TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_assistente_conexao_credencial_hash UNIQUE (hash),
    CONSTRAINT ck_assistente_conexao_credencial_tipo CHECK (tipo IN ('ACESSO', 'RENOVACAO', 'CHAVE'))
);

CREATE INDEX idx_assistente_conexao_credencial_conexao ON assistente_conexao_credencial (conexao_id);

CREATE TABLE assistente_conexao_codigo (
    id UUID PRIMARY KEY,
    hash VARCHAR(64) NOT NULL,
    client_id VARCHAR(80) NOT NULL,
    usuario_id UUID NOT NULL,
    permite_escrita BOOLEAN NOT NULL DEFAULT FALSE,
    redirect_uri TEXT NOT NULL,
    code_challenge VARCHAR(128) NOT NULL,
    recurso VARCHAR(500),
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    usado_em TIMESTAMP WITH TIME ZONE,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_assistente_conexao_codigo_hash UNIQUE (hash)
);

-- Conversa técnica das conexões: fora do histórico do Copiloto.
ALTER TABLE assistente_conversa DROP CONSTRAINT ck_assistente_conversa_origem;
ALTER TABLE assistente_conversa ADD CONSTRAINT ck_assistente_conversa_origem
    CHECK (origem IN ('WORKSPACE', 'DASHBOARD', 'MCP'));

-- Permissões: conectar a própria IA acompanha quem já usa o Copiloto;
-- ver e desconectar as conexões de todos fica com o administrador.
INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'assistente:conexao:use'),
       (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'assistente:conexao:manage')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pp.id_perfil, nova.id
FROM perfil_permissoes pp
JOIN permissoes chat ON chat.id = pp.id_permissao AND chat.nome = 'assistente:chat:use'
CROSS JOIN permissoes nova
WHERE nova.nome = 'assistente:conexao:use'
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome = 'ADMINISTRADOR'
  AND permissao.nome IN ('assistente:conexao:use', 'assistente:conexao:manage')
ON CONFLICT DO NOTHING;
