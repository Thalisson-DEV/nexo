-- Catálogo e matriz de treinamentos da governança de CIPA e Brigada.

CREATE TABLE sst_treinamentos_governanca (
    id UUID PRIMARY KEY,
    codigo VARCHAR(80) NOT NULL,
    nome VARCHAR(160) NOT NULL,
    nome_normalizado VARCHAR(160) NOT NULL,
    descricao VARCHAR(500),
    aplica_cipa BOOLEAN NOT NULL DEFAULT FALSE,
    aplica_brigada BOOLEAN NOT NULL DEFAULT FALSE,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_sst_treinamento_codigo UNIQUE (codigo),
    CONSTRAINT ck_sst_treinamento_contexto CHECK (aplica_cipa OR aplica_brigada)
);

CREATE UNIQUE INDEX ux_sst_treinamento_nome_ativo
    ON sst_treinamentos_governanca (nome_normalizado)
    WHERE ativo;
CREATE INDEX ix_sst_treinamento_contexto
    ON sst_treinamentos_governanca (aplica_cipa, aplica_brigada, ativo);

CREATE TABLE sst_exigencias_treinamento (
    id UUID PRIMARY KEY,
    id_grupo_versao UUID NOT NULL,
    revisao_exigencia INTEGER NOT NULL,
    id_exigencia_anterior UUID REFERENCES sst_exigencias_treinamento(id),
    atual BOOLEAN NOT NULL DEFAULT TRUE,

    id_treinamento UUID NOT NULL REFERENCES sst_treinamentos_governanca(id),
    tipo_corpo VARCHAR(20) NOT NULL,
    modo_aplicabilidade VARCHAR(30) NOT NULL,
    id_funcao UUID,
    papel_corpo VARCHAR(60),
    id_base UUID,

    carga_horaria_minutos INTEGER NOT NULL,
    politica_instituicao VARCHAR(20) NOT NULL,
    modalidade VARCHAR(30),
    recorrencia_meses INTEGER,
    antecedencia_alerta_dias INTEGER NOT NULL DEFAULT 30,
    vigencia_inicio DATE NOT NULL,
    vigencia_fim DATE,

    tipo_fonte VARCHAR(20) NOT NULL,
    jurisdicao VARCHAR(60),
    identificador_fonte VARCHAR(160) NOT NULL,
    edicao_fonte VARCHAR(80),
    url_fonte VARCHAR(500),
    fonte_confirmada BOOLEAN NOT NULL DEFAULT FALSE,
    usuario_confirmacao_fonte UUID,
    data_confirmacao_fonte TIMESTAMP WITH TIME ZONE,
    observacoes VARCHAR(500),

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_sst_exigencia_revisao UNIQUE (id_grupo_versao, revisao_exigencia),
    CONSTRAINT ck_sst_exigencia_tipo_corpo CHECK (tipo_corpo IN ('CIPA', 'BRIGADA')),
    CONSTRAINT ck_sst_exigencia_modo
        CHECK (modo_aplicabilidade IN ('FUNCTION', 'BODY_ROLE', 'FUNCTION_AND_BODY_ROLE')),
    CONSTRAINT ck_sst_exigencia_seletores CHECK (
        (modo_aplicabilidade = 'FUNCTION' AND id_funcao IS NOT NULL AND papel_corpo IS NULL) OR
        (modo_aplicabilidade = 'BODY_ROLE' AND id_funcao IS NULL AND papel_corpo IS NOT NULL) OR
        (modo_aplicabilidade = 'FUNCTION_AND_BODY_ROLE' AND id_funcao IS NOT NULL AND papel_corpo IS NOT NULL)
    ),
    CONSTRAINT ck_sst_exigencia_carga CHECK (carga_horaria_minutos > 0),
    CONSTRAINT ck_sst_exigencia_politica_instituicao
        CHECK (politica_instituicao IN ('QUALQUER', 'RESTRITA')),
    CONSTRAINT ck_sst_exigencia_recorrencia
        CHECK (recorrencia_meses IS NULL OR recorrencia_meses > 0),
    CONSTRAINT ck_sst_exigencia_alerta
        CHECK (antecedencia_alerta_dias >= 0),
    CONSTRAINT ck_sst_exigencia_vigencia
        CHECK (vigencia_fim IS NULL OR vigencia_fim >= vigencia_inicio),
    CONSTRAINT ck_sst_exigencia_fonte
        CHECK (tipo_fonte IN ('NORMATIVA', 'CONTRATUAL', 'INTERNA')),
    CONSTRAINT ck_sst_exigencia_confirmacao_fonte
        CHECK ((fonte_confirmada = FALSE) OR
               (usuario_confirmacao_fonte IS NOT NULL AND data_confirmacao_fonte IS NOT NULL))
);

CREATE UNIQUE INDEX ux_sst_exigencia_atual
    ON sst_exigencias_treinamento (id_grupo_versao)
    WHERE atual AND ativo;
CREATE INDEX ix_sst_exigencia_treinamento
    ON sst_exigencias_treinamento (id_treinamento, atual, ativo);
CREATE INDEX ix_sst_exigencia_funcao
    ON sst_exigencias_treinamento (id_funcao, atual, ativo);
CREATE INDEX ix_sst_exigencia_papel
    ON sst_exigencias_treinamento (papel_corpo, tipo_corpo, atual, ativo);
CREATE INDEX ix_sst_exigencia_base
    ON sst_exigencias_treinamento (id_base, atual, ativo);
CREATE INDEX ix_sst_exigencia_vigencia
    ON sst_exigencias_treinamento (vigencia_inicio, vigencia_fim);

CREATE TABLE sst_exigencia_instituicoes (
    id_exigencia UUID NOT NULL REFERENCES sst_exigencias_treinamento(id) ON DELETE RESTRICT,
    instituicao_normalizada VARCHAR(200) NOT NULL,
    PRIMARY KEY (id_exigencia, instituicao_normalizada)
);

-- O campo existe desde V96 para manter o agregado Documento independente da ordem
-- de implantação. A FK só pode ser criada depois do catálogo.
ALTER TABLE sst_documentos_governanca
    ADD CONSTRAINT fk_sst_documento_treinamento
    FOREIGN KEY (id_treinamento) REFERENCES sst_treinamentos_governanca(id);
