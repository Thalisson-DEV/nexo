-- Políticas separam a decisão de audiência do conteúdo da regra.
-- Uma política pertence a um gatilho, possui rotas condicionais ordenadas e um fallback.

CREATE TABLE IF NOT EXISTS politicas_roteamento_notificacao (
    id UUID PRIMARY KEY,
    chave_gatilho VARCHAR(80) NOT NULL,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_politicas_roteamento_nome
    ON politicas_roteamento_notificacao (LOWER(nome));

CREATE INDEX IF NOT EXISTS idx_politicas_roteamento_gatilho
    ON politicas_roteamento_notificacao (chave_gatilho)
    WHERE ativo = TRUE;

CREATE TABLE IF NOT EXISTS rotas_roteamento_notificacao (
    id UUID PRIMARY KEY,
    id_politica UUID NOT NULL
        REFERENCES politicas_roteamento_notificacao(id) ON DELETE CASCADE,
    ordem INTEGER NOT NULL CHECK (ordem >= 0),
    padrao BOOLEAN NOT NULL DEFAULT FALSE,
    id_usuario UUID NOT NULL REFERENCES usuarios(id),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_rotas_roteamento_ordem UNIQUE (id_politica, ordem)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rotas_roteamento_padrao
    ON rotas_roteamento_notificacao (id_politica)
    WHERE padrao = TRUE AND ativo = TRUE;

CREATE INDEX IF NOT EXISTS idx_rotas_roteamento_politica
    ON rotas_roteamento_notificacao (id_politica)
    WHERE ativo = TRUE;

CREATE TABLE IF NOT EXISTS condicoes_rota_roteamento_notificacao (
    id UUID PRIMARY KEY,
    id_rota UUID NOT NULL
        REFERENCES rotas_roteamento_notificacao(id) ON DELETE CASCADE,
    campo VARCHAR(60) NOT NULL,
    operador VARCHAR(20) NOT NULL,
    valor VARCHAR(500) NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_condicoes_rota_operador CHECK (operador IN (
        'IGUAL', 'DIFERENTE', 'MAIOR_QUE', 'MAIOR_OU_IGUAL',
        'MENOR_QUE', 'MENOR_OU_IGUAL', 'CONTEM', 'EM'))
);

CREATE INDEX IF NOT EXISTS idx_condicoes_rota_roteamento
    ON condicoes_rota_roteamento_notificacao (id_rota)
    WHERE ativo = TRUE;

ALTER TABLE destinatarios_regra_notificacao
    DROP CONSTRAINT IF EXISTS ck_destinatarios_regra_tipo;

ALTER TABLE destinatarios_regra_notificacao
    DROP CONSTRAINT IF EXISTS ck_destinatarios_regra_alvo;

ALTER TABLE destinatarios_regra_notificacao
    ADD CONSTRAINT ck_destinatarios_regra_tipo CHECK (tipo IN (
        'PERMISSAO', 'PERFIL', 'USUARIO', 'PAPEL_DINAMICO', 'POLITICA'));

ALTER TABLE destinatarios_regra_notificacao
    ADD CONSTRAINT ck_destinatarios_regra_alvo CHECK (
        (tipo IN ('PERFIL', 'USUARIO', 'POLITICA') AND id_alvo IS NOT NULL)
        OR (tipo IN ('PERMISSAO', 'PAPEL_DINAMICO') AND valor_alvo IS NOT NULL));

-- A FK polimórfica de id_alvo continua validada pela aplicação: PERFIL e USUARIO
-- já compartilham essa coluna. Para POLITICA, o caso de uso exige existência,
-- atividade e o mesmo gatilho antes de salvar ou ativar a regra.
