-- V73: substrato de entrega do sistema de notificações.
--
-- Esta é a Fase 1: a caixa de entrada por usuário e as preferências de recebimento.
-- As duas origens que alimentam esta tabela entram nas fases seguintes — o mural de
-- comunicados (escrito por administradores) e o motor de regras (disparado por
-- eventos de domínio). Ambas gravam aqui, por isso a tabela nasce genérica o
-- bastante para as duas e a coluna `origem` diz de onde a linha veio.

CREATE TABLE notificacoes (
    id UUID PRIMARY KEY,
    -- Sem FK para usuarios: a convenção do projeto é não cruzar módulos no schema.
    id_usuario UUID NOT NULL,
    origem VARCHAR(20) NOT NULL,
    -- Preenchidos pelas fases 2 e 3: a regra que gerou e o agregado de origem
    -- (o desconto, o desvio, o comunicado) para o front montar o deeplink.
    id_regra UUID,
    id_referencia UUID,
    categoria VARCHAR(30) NOT NULL,
    prioridade VARCHAR(20) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    corpo VARCHAR(1000) NOT NULL,
    deeplink VARCHAR(500),
    contexto JSONB,
    -- Impede que o reprocessamento do outbox (event_publication) entregue a mesma
    -- notificação duas vezes ao mesmo usuário.
    chave_idempotencia VARCHAR(200) NOT NULL,
    lida_em TIMESTAMP WITH TIME ZONE,
    arquivada_em TIMESTAMP WITH TIME ZONE,
    expira_em TIMESTAMP WITH TIME ZONE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_notificacoes_origem CHECK (origem IN ('REGRA', 'COMUNICADO', 'SISTEMA')),
    CONSTRAINT ck_notificacoes_prioridade CHECK (prioridade IN ('BAIXA', 'NORMAL', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_notificacoes_categoria CHECK (categoria IN (
        'APROVACAO', 'SEGURANCA', 'OPERACIONAL', 'CHAMADO', 'COMUNICADO', 'SISTEMA')),
    CONSTRAINT uk_notificacoes_idempotencia UNIQUE (id_usuario, chave_idempotencia)
);

CREATE TABLE preferencias_notificacao (
    id UUID PRIMARY KEY,
    id_usuario UUID NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    in_app BOOLEAN NOT NULL DEFAULT TRUE,
    -- Canal ainda não implementado: a coluna existe para que ligar e-mail no futuro
    -- seja um adaptador novo, e não uma migration de estrutura.
    email BOOLEAN NOT NULL DEFAULT FALSE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_preferencias_notificacao_categoria CHECK (categoria IN (
        'APROVACAO', 'SEGURANCA', 'OPERACIONAL', 'CHAMADO', 'COMUNICADO', 'SISTEMA')),
    CONSTRAINT uk_preferencias_notificacao UNIQUE (id_usuario, categoria)
);

-- Listagem da caixa: sempre por usuário, sempre da mais recente para a mais antiga.
CREATE INDEX idx_notificacoes_caixa
    ON notificacoes (id_usuario, data_criacao DESC)
    WHERE ativo = TRUE AND arquivada_em IS NULL;

-- O contador do sininho é a consulta mais frequente do módulo; o índice parcial
-- mantém a leitura barata mesmo com a tabela grande.
CREATE INDEX idx_notificacoes_nao_lidas
    ON notificacoes (id_usuario)
    WHERE ativo = TRUE AND lida_em IS NULL AND arquivada_em IS NULL;

-- Varredura do expurgo agendado.
CREATE INDEX idx_notificacoes_expurgo
    ON notificacoes (data_criacao)
    WHERE ativo = TRUE;

CREATE INDEX idx_preferencias_notificacao_usuario
    ON preferencias_notificacao (id_usuario)
    WHERE ativo = TRUE;
