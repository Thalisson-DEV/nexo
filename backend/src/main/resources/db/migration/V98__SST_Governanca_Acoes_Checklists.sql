-- =====================================================================
-- V98: Ações e checklists próprios da governança de CIPA e Brigada.
-- A execução é separada de inspecoes_seguranca porque uma inspeção
-- predial não possui colaborador inspecionado nem processo operacional.
-- =====================================================================

CREATE TABLE acoes_governanca_sst (
    id UUID PRIMARY KEY,
    id_corpo UUID NOT NULL REFERENCES corpos_governanca_sst(id),
    id_base_operacional UUID NOT NULL,
    id_reuniao UUID REFERENCES reunioes_governanca_sst(id),
    id_execucao_checklist UUID,
    client_request_id UUID,
    titulo VARCHAR(180) NOT NULL,
    descricao VARCHAR(2000) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    prioridade VARCHAR(20) NOT NULL,
    id_responsavel UUID NOT NULL,
    prazo DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    resultado VARCHAR(2000),
    motivo_cancelamento VARCHAR(1000),
    data_inicio TIMESTAMP WITH TIME ZONE,
    data_conclusao TIMESTAMP WITH TIME ZONE,
    id_usuario_conclusao UUID,
    data_cancelamento TIMESTAMP WITH TIME ZONE,
    id_usuario_cancelamento UUID,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_acao_governanca_tipo CHECK (tipo IN ('CORRETIVA', 'PREVENTIVA', 'INSPECAO', 'OUTRA')),
    CONSTRAINT ck_acao_governanca_prioridade CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_acao_governanca_status CHECK (status IN ('ABERTA', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA')),
    CONSTRAINT ck_acao_governanca_conclusao CHECK (
        status <> 'CONCLUIDA' OR (data_conclusao IS NOT NULL AND id_usuario_conclusao IS NOT NULL AND resultado IS NOT NULL)
    ),
    CONSTRAINT ck_acao_governanca_cancelamento CHECK (
        status <> 'CANCELADA' OR (data_cancelamento IS NOT NULL AND id_usuario_cancelamento IS NOT NULL AND motivo_cancelamento IS NOT NULL)
    ),
    CONSTRAINT uk_acao_governanca_cliente UNIQUE (id_corpo, client_request_id)
);

CREATE TABLE modelos_checklist_governanca_sst (
    id UUID PRIMARY KEY,
    id_formulario UUID NOT NULL REFERENCES formularios_inspecao(id),
    tipo_corpo VARCHAR(20) NOT NULL,
    nome VARCHAR(180) NOT NULL,
    orientacao VARCHAR(1000),
    referencia_normativa VARCHAR(500),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_modelo_checklist_tipo_corpo CHECK (tipo_corpo IN ('CIPA', 'BRIGADA'))
);

CREATE TABLE execucoes_checklist_governanca_sst (
    id UUID PRIMARY KEY,
    id_modelo UUID NOT NULL REFERENCES modelos_checklist_governanca_sst(id),
    id_formulario UUID NOT NULL REFERENCES formularios_inspecao(id),
    id_corpo UUID NOT NULL REFERENCES corpos_governanca_sst(id),
    id_base_operacional UUID NOT NULL,
    id_executor UUID NOT NULL,
    client_request_id UUID,
    data_execucao TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    observacoes VARCHAR(2000),
    data_conclusao TIMESTAMP WITH TIME ZONE,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_execucao_checklist_status CHECK (status IN ('RASCUNHO', 'CONCLUIDA', 'CANCELADA')),
    CONSTRAINT ck_execucao_checklist_conclusao CHECK (status <> 'CONCLUIDA' OR data_conclusao IS NOT NULL),
    CONSTRAINT uk_execucao_checklist_cliente UNIQUE (id_corpo, client_request_id)
);

ALTER TABLE acoes_governanca_sst
    ADD CONSTRAINT fk_acao_execucao_checklist
    FOREIGN KEY (id_execucao_checklist) REFERENCES execucoes_checklist_governanca_sst(id);

CREATE TABLE respostas_checklist_governanca_sst (
    id UUID PRIMARY KEY,
    id_execucao UUID NOT NULL REFERENCES execucoes_checklist_governanca_sst(id),
    id_pergunta UUID NOT NULL,
    pergunta_snapshot VARCHAR(500) NOT NULL,
    tipo_resposta VARCHAR(30) NOT NULL,
    obrigatoria BOOLEAN NOT NULL,
    valor_texto VARCHAR(2000),
    valor_numero NUMERIC(18,4),
    valor_data DATE,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_resposta_checklist_pergunta UNIQUE (id_execucao, id_pergunta)
);

CREATE TABLE respostas_opcoes_checklist_governanca_sst (
    id UUID PRIMARY KEY,
    id_resposta UUID NOT NULL REFERENCES respostas_checklist_governanca_sst(id),
    id_opcao UUID NOT NULL,
    opcao_snapshot VARCHAR(255) NOT NULL,
    nao_conforme BOOLEAN NOT NULL DEFAULT FALSE,
    gravidade VARCHAR(20),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_resposta_checklist_opcao UNIQUE (id_resposta, id_opcao)
);

CREATE TABLE constatacoes_checklist_governanca_sst (
    id UUID PRIMARY KEY,
    id_execucao UUID NOT NULL REFERENCES execucoes_checklist_governanca_sst(id),
    id_resposta UUID NOT NULL REFERENCES respostas_checklist_governanca_sst(id),
    id_opcao UUID,
    pergunta_snapshot VARCHAR(500) NOT NULL,
    opcao_snapshot VARCHAR(255),
    gravidade VARCHAR(20),
    descricao VARCHAR(2000) NOT NULL,
    id_acao UUID UNIQUE REFERENCES acoes_governanca_sst(id),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_constatacao_checklist_origem UNIQUE (id_execucao, id_resposta, id_opcao)
);

CREATE TABLE evidencias_governanca_sst (
    id UUID PRIMARY KEY,
    id_acao UUID REFERENCES acoes_governanca_sst(id),
    id_execucao UUID REFERENCES execucoes_checklist_governanca_sst(id),
    id_usuario_envio UUID NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    object_key VARCHAR(700) NOT NULL UNIQUE,
    content_type VARCHAR(120) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    hash_conteudo VARCHAR(64) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_evidencia_governanca_tamanho CHECK (tamanho_bytes > 0),
    CONSTRAINT ck_evidencia_governanca_dono CHECK (
        (id_acao IS NOT NULL AND id_execucao IS NULL) OR
        (id_acao IS NULL AND id_execucao IS NOT NULL)
    )
);

CREATE INDEX idx_acao_governanca_corpo_status ON acoes_governanca_sst (id_corpo, status);
CREATE INDEX idx_acao_governanca_base_prazo ON acoes_governanca_sst (id_base_operacional, prazo);
CREATE INDEX idx_acao_governanca_responsavel ON acoes_governanca_sst (id_responsavel, status);
CREATE INDEX idx_modelo_checklist_formulario ON modelos_checklist_governanca_sst (id_formulario);
CREATE INDEX idx_execucao_checklist_corpo_data ON execucoes_checklist_governanca_sst (id_corpo, data_execucao);
CREATE INDEX idx_execucao_checklist_formulario ON execucoes_checklist_governanca_sst (id_formulario);
CREATE INDEX idx_resposta_checklist_execucao ON respostas_checklist_governanca_sst (id_execucao);
CREATE INDEX idx_constatacao_checklist_execucao ON constatacoes_checklist_governanca_sst (id_execucao);
CREATE INDEX idx_evidencia_governanca_acao ON evidencias_governanca_sst (id_acao);
CREATE INDEX idx_evidencia_governanca_execucao ON evidencias_governanca_sst (id_execucao);
