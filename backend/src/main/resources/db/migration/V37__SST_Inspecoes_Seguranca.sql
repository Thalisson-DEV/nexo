-- =====================================================================
-- V37: Módulo SST - Inspeções de Segurança
-- Template dinâmico (formulário -> grupos -> perguntas -> opções) e
-- execução das inspeções (respostas e desvios atribuídos a colaboradores).
-- =====================================================================

-- Colunas de auditoria padrão da BaseEntity (reutilizadas em todas as tabelas):
--   versao, data_criacao, data_ultima_alteracao, usuario_criacao,
--   usuario_ultima_alteracao, ativo, data_desativacao

-- ---------------------------------------------------------------------
-- 1. Template do formulário
-- ---------------------------------------------------------------------
CREATE TABLE formularios_inspecao (
    id UUID PRIMARY KEY,
    titulo VARCHAR(150) NOT NULL,
    descricao VARCHAR(500),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE grupos_pergunta (
    id UUID PRIMARY KEY,
    id_formulario UUID NOT NULL REFERENCES formularios_inspecao(id),
    titulo VARCHAR(150) NOT NULL,
    ordem INTEGER NOT NULL DEFAULT 0,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE perguntas_inspecao (
    id UUID PRIMARY KEY,
    id_grupo UUID NOT NULL REFERENCES grupos_pergunta(id),
    enunciado VARCHAR(500) NOT NULL,
    tipo_resposta VARCHAR(20) NOT NULL,
    obrigatoria BOOLEAN NOT NULL DEFAULT FALSE,
    ordem INTEGER NOT NULL DEFAULT 0,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE opcoes_resposta (
    id UUID PRIMARY KEY,
    id_pergunta UUID NOT NULL REFERENCES perguntas_inspecao(id),
    texto VARCHAR(255) NOT NULL,
    gera_desvio BOOLEAN NOT NULL DEFAULT FALSE,
    ordem INTEGER NOT NULL DEFAULT 0,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

-- ---------------------------------------------------------------------
-- 2. Execução da inspeção
-- ---------------------------------------------------------------------
CREATE TABLE inspecoes_seguranca (
    id UUID PRIMARY KEY,
    id_formulario UUID NOT NULL REFERENCES formularios_inspecao(id),
    id_processo UUID NOT NULL,
    id_inspetor UUID NOT NULL,
    id_veiculo UUID,
    id_equipe UUID,
    data_realizacao TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    observacoes VARCHAR(1000),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE inspecao_colaboradores (
    id UUID PRIMARY KEY,
    id_inspecao UUID NOT NULL REFERENCES inspecoes_seguranca(id),
    id_colaborador UUID NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_inspecao_colaborador UNIQUE (id_inspecao, id_colaborador)
);

CREATE TABLE respostas_inspecao (
    id UUID PRIMARY KEY,
    id_inspecao UUID NOT NULL REFERENCES inspecoes_seguranca(id),
    id_pergunta UUID NOT NULL,
    valor_texto VARCHAR(1000),
    valor_numero DECIMAL(18, 4),
    valor_data DATE,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_resposta_pergunta UNIQUE (id_inspecao, id_pergunta)
);

CREATE TABLE respostas_opcoes (
    id UUID PRIMARY KEY,
    id_resposta UUID NOT NULL REFERENCES respostas_inspecao(id),
    id_opcao_resposta UUID NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE desvios_seguranca (
    id UUID PRIMARY KEY,
    id_inspecao UUID NOT NULL REFERENCES inspecoes_seguranca(id),
    id_pergunta UUID NOT NULL,
    id_opcao_resposta UUID,
    gravidade VARCHAR(20) NOT NULL,
    descricao VARCHAR(1000),
    acao_corretiva VARCHAR(1000),
    prazo DATE,
    status VARCHAR(20) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE desvio_colaboradores (
    id UUID PRIMARY KEY,
    id_desvio UUID NOT NULL REFERENCES desvios_seguranca(id),
    id_colaborador UUID NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_desvio_colaborador UNIQUE (id_desvio, id_colaborador)
);

-- ---------------------------------------------------------------------
-- 3. Índices
-- ---------------------------------------------------------------------
CREATE INDEX idx_grupos_pergunta_formulario ON grupos_pergunta(id_formulario);
CREATE INDEX idx_perguntas_grupo ON perguntas_inspecao(id_grupo);
CREATE INDEX idx_opcoes_pergunta ON opcoes_resposta(id_pergunta);

CREATE INDEX idx_inspecoes_formulario ON inspecoes_seguranca(id_formulario);
CREATE INDEX idx_inspecoes_processo ON inspecoes_seguranca(id_processo);
CREATE INDEX idx_inspecoes_inspetor ON inspecoes_seguranca(id_inspetor);
CREATE INDEX idx_inspecoes_equipe ON inspecoes_seguranca(id_equipe);
CREATE INDEX idx_inspecoes_veiculo ON inspecoes_seguranca(id_veiculo);
CREATE INDEX idx_inspecoes_data ON inspecoes_seguranca(data_realizacao);
CREATE INDEX idx_inspecoes_status ON inspecoes_seguranca(status);

CREATE INDEX idx_inspecao_colab_inspecao ON inspecao_colaboradores(id_inspecao);
CREATE INDEX idx_inspecao_colab_colaborador ON inspecao_colaboradores(id_colaborador);
CREATE INDEX idx_respostas_inspecao ON respostas_inspecao(id_inspecao);
CREATE INDEX idx_respostas_opcoes_resposta ON respostas_opcoes(id_resposta);
CREATE INDEX idx_desvios_inspecao ON desvios_seguranca(id_inspecao);
CREATE INDEX idx_desvios_status ON desvios_seguranca(status);
CREATE INDEX idx_desvio_colab_desvio ON desvio_colaboradores(id_desvio);
CREATE INDEX idx_desvio_colab_colaborador ON desvio_colaboradores(id_colaborador);
