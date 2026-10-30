-- V76: motor de regras — a origem "de sistema" das notificações.
--
-- Fase 3. Uma regra diz o que fazer quando um gatilho acontece: para quem avisar,
-- sob que condições, com que texto e com que intervalo mínimo entre avisos. O
-- gatilho em si NÃO é cadastrável — vem do enum GatilhoNotificacao, porque cada um
-- exige código (um módulo publicando o evento, ou um módulo sabendo medir a
-- situação). É essa fronteira que permite deixar todo o resto configurável sem
-- transformar a tela de regras em execução de código arbitrário.

CREATE TABLE regras_notificacao (
    id UUID PRIMARY KEY,
    -- Sem CHECK com a lista de gatilhos de propósito: acrescentar um gatilho já exige
    -- deploy (o código que o dispara), então repetir o catálogo aqui só criaria uma
    -- migration obrigatória a cada gatilho novo, sem ganho de integridade real.
    -- A validação é feita na conversão para o enum, ao salvar a regra.
    chave_gatilho VARCHAR(80) NOT NULL,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500),
    -- Regra de fábrica: editável e desligável, nunca removível.
    sistema BOOLEAN NOT NULL DEFAULT FALSE,
    categoria VARCHAR(30) NOT NULL,
    prioridade VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    titulo_template VARCHAR(150) NOT NULL,
    corpo_template VARCHAR(1000) NOT NULL,
    deeplink_template VARCHAR(500),
    -- Só para gatilhos POR_LIMIAR.
    limiar INTEGER CHECK (limiar IS NULL OR limiar >= 1),
    cooldown_minutos INTEGER NOT NULL DEFAULT 240 CHECK (cooldown_minutos >= 0),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_regras_notificacao_categoria CHECK (categoria IN (
        'APROVACAO', 'SEGURANCA', 'OPERACIONAL', 'CHAMADO', 'COMUNICADO', 'SISTEMA')),
    CONSTRAINT ck_regras_notificacao_prioridade CHECK (prioridade IN (
        'BAIXA', 'NORMAL', 'ALTA', 'CRITICA'))
);

-- Filtros sobre os dados do gatilho. Todas precisam ser satisfeitas (E lógico).
CREATE TABLE condicoes_regra_notificacao (
    id UUID PRIMARY KEY,
    id_regra UUID NOT NULL REFERENCES regras_notificacao(id),
    campo VARCHAR(60) NOT NULL,
    operador VARCHAR(20) NOT NULL,
    -- Sempre texto; a comparação decide o tipo em tempo de avaliação.
    valor VARCHAR(500) NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_condicoes_regra_operador CHECK (operador IN (
        'IGUAL', 'DIFERENTE', 'MAIOR_QUE', 'MAIOR_OU_IGUAL',
        'MENOR_QUE', 'MENOR_OU_IGUAL', 'CONTEM', 'EM'))
);

CREATE TABLE destinatarios_regra_notificacao (
    id UUID PRIMARY KEY,
    id_regra UUID NOT NULL REFERENCES regras_notificacao(id),
    tipo VARCHAR(20) NOT NULL,
    -- PERFIL e USUARIO usam id_alvo; PERMISSAO e PAPEL_DINAMICO usam valor_alvo.
    id_alvo UUID,
    valor_alvo VARCHAR(120),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_destinatarios_regra_tipo CHECK (tipo IN (
        'PERMISSAO', 'PERFIL', 'USUARIO', 'PAPEL_DINAMICO')),
    CONSTRAINT ck_destinatarios_regra_alvo CHECK (
        (tipo IN ('PERFIL', 'USUARIO') AND id_alvo IS NOT NULL)
        OR (tipo IN ('PERMISSAO', 'PAPEL_DINAMICO') AND valor_alvo IS NOT NULL))
);

-- Memória do último disparo, por escopo — é o que dá sentido ao cooldown.
-- Escopo = id do registro de origem (regras por ocorrência) ou 'GLOBAL' (por limiar).
CREATE TABLE execucoes_regra_notificacao (
    id UUID PRIMARY KEY,
    id_regra UUID NOT NULL REFERENCES regras_notificacao(id),
    chave_escopo VARCHAR(100) NOT NULL,
    ultima_execucao_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_execucoes_regra_escopo UNIQUE (id_regra, chave_escopo)
);

-- O motor carrega as regras vivas de um gatilho a cada evento e a cada varredura.
CREATE INDEX idx_regras_notificacao_gatilho
    ON regras_notificacao (chave_gatilho)
    WHERE ativo = TRUE;

CREATE INDEX idx_condicoes_regra_notificacao_regra
    ON condicoes_regra_notificacao (id_regra)
    WHERE ativo = TRUE;

CREATE INDEX idx_destinatarios_regra_notificacao_regra
    ON destinatarios_regra_notificacao (id_regra)
    WHERE ativo = TRUE;
