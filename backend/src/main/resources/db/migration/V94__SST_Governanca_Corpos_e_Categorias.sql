-- =====================================================================
-- V94: fundação da governança de CIPA e Brigada
--
-- Referências ao RH (base, função e colaborador) são UUIDs sem FK: a
-- validação pertence às APIs públicas do módulo proprietário. Nomes de
-- pessoas e funções são snapshots cifrados pela aplicação.
-- =====================================================================

CREATE TABLE perfis_normativos_governanca_sst (
    id UUID PRIMARY KEY,
    codigo VARCHAR(60) NOT NULL,
    codigo_normalizado VARCHAR(60) NOT NULL,
    versao_perfil INTEGER NOT NULL,
    escopo VARCHAR(20) NOT NULL,
    tipo_corpo VARCHAR(20) NOT NULL,
    jurisdicao VARCHAR(20),
    orgao_emissor VARCHAR(120),
    ato_normativo VARCHAR(180),
    edicao VARCHAR(80) NOT NULL,
    inicio_vigencia DATE NOT NULL,
    fim_vigencia DATE,
    url_oficial VARCHAR(1000),
    confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    data_confirmacao TIMESTAMP WITH TIME ZONE,
    id_usuario_confirmacao UUID,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_perfil_governanca_versao CHECK (versao_perfil > 0),
    CONSTRAINT ck_perfil_governanca_vigencia CHECK (
        fim_vigencia IS NULL OR fim_vigencia >= inicio_vigencia),
    CONSTRAINT ck_perfil_governanca_tipo CHECK (tipo_corpo IN ('CIPA', 'BRIGADA')),
    CONSTRAINT ck_perfil_governanca_escopo CHECK (escopo IN ('NACIONAL', 'ESTADUAL', 'INTERNO')),
    CONSTRAINT ck_perfil_governanca_estado CHECK (
        escopo <> 'ESTADUAL' OR jurisdicao IS NOT NULL)
);

CREATE UNIQUE INDEX ux_perfil_governanca_codigo_versao_ativo
    ON perfis_normativos_governanca_sst (codigo_normalizado, versao_perfil)
    WHERE ativo;
CREATE INDEX ix_perfil_governanca_aplicacao
    ON perfis_normativos_governanca_sst (tipo_corpo, escopo, jurisdicao, inicio_vigencia, fim_vigencia);

CREATE TABLE perfil_normativo_papeis_governanca_sst (
    id_perfil_normativo UUID NOT NULL,
    papel VARCHAR(40) NOT NULL,
    quantidade_minima INTEGER NOT NULL,

    CONSTRAINT pk_perfil_normativo_papel PRIMARY KEY (id_perfil_normativo, papel),
    CONSTRAINT fk_perfil_normativo_papel_perfil FOREIGN KEY (id_perfil_normativo)
        REFERENCES perfis_normativos_governanca_sst (id),
    CONSTRAINT ck_perfil_normativo_papel_quantidade CHECK (quantidade_minima > 0)
);

CREATE TABLE corpos_governanca_sst (
    id UUID PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    regime_composicao VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    nome VARCHAR(150) NOT NULL,

    id_base_operacional UUID NOT NULL,
    base_nome VARCHAR(120) NOT NULL,
    unidade_governanca VARCHAR(180) NOT NULL,
    unidade_governanca_normalizada VARCHAR(180) NOT NULL,
    uf VARCHAR(2) NOT NULL,
    inicio_periodo DATE NOT NULL,
    fim_periodo DATE NOT NULL,
    grau_risco INTEGER,

    id_perfil_normativo UUID,
    hipotese_representante_confirmada BOOLEAN NOT NULL DEFAULT FALSE,
    conformidade_normativa_validada BOOLEAN NOT NULL DEFAULT FALSE,
    codigo_avaliacao_normativa VARCHAR(100),
    perfil_codigo_snapshot VARCHAR(60),
    perfil_edicao_snapshot VARCHAR(80),
    perfil_url_snapshot VARCHAR(1000),

    data_ativacao TIMESTAMP WITH TIME ZONE,
    id_usuario_ativacao UUID,
    data_ultima_movimentacao_composicao TIMESTAMP WITH TIME ZONE,
    data_encerramento TIMESTAMP WITH TIME ZONE,
    data_efeito_encerramento DATE,
    id_usuario_encerramento UUID,
    motivo_encerramento VARCHAR(50),
    justificativa_encerramento VARCHAR(2048),       -- cifrado
    referencia_evidencia_encerramento VARCHAR(255),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_corpo_governanca_perfil FOREIGN KEY (id_perfil_normativo)
        REFERENCES perfis_normativos_governanca_sst (id),
    CONSTRAINT ck_corpo_governanca_tipo CHECK (tipo IN ('CIPA', 'BRIGADA')),
    CONSTRAINT ck_corpo_governanca_status CHECK (status IN ('EM_PREPARACAO', 'ATIVO', 'ENCERRADO')),
    CONSTRAINT ck_corpo_governanca_regime CHECK (
        (tipo = 'CIPA' AND regime_composicao IN ('CIPA_DIMENSIONADA', 'REPRESENTANTE_NOMEADO'))
        OR (tipo = 'BRIGADA' AND regime_composicao IS NULL)),
    CONSTRAINT ck_corpo_governanca_periodo CHECK (fim_periodo >= inicio_periodo),
    CONSTRAINT ck_corpo_governanca_grau_risco CHECK (grau_risco IS NULL OR grau_risco BETWEEN 1 AND 4)
);

-- Barreira final contra duas ativações concorrentes. Preparações e ciclos
-- encerrados podem coexistir sem disputar a chave.
CREATE UNIQUE INDEX ux_corpo_governanca_unidade_ativa
    ON corpos_governanca_sst (tipo, id_base_operacional, unidade_governanca_normalizada)
    WHERE status = 'ATIVO' AND ativo;

CREATE INDEX ix_corpo_governanca_base_status
    ON corpos_governanca_sst (id_base_operacional, status);
CREATE INDEX ix_corpo_governanca_tipo_periodo
    ON corpos_governanca_sst (tipo, inicio_periodo, fim_periodo);
CREATE INDEX ix_corpo_governanca_perfil
    ON corpos_governanca_sst (id_perfil_normativo);

CREATE TABLE bases_abrangidas_corpo_sst (
    id UUID PRIMARY KEY,
    id_corpo UUID NOT NULL,
    id_base_operacional UUID NOT NULL,
    base_nome VARCHAR(120) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_base_abrangida_corpo FOREIGN KEY (id_corpo)
        REFERENCES corpos_governanca_sst (id),
    CONSTRAINT uk_base_abrangida_corpo UNIQUE (id_corpo, id_base_operacional)
);

CREATE INDEX ix_base_abrangida_base
    ON bases_abrangidas_corpo_sst (id_base_operacional);

CREATE TABLE membros_corpo_sst (
    id UUID PRIMARY KEY,
    id_corpo UUID NOT NULL,
    id_colaborador UUID NOT NULL,

    nome_snapshot VARCHAR(512) NOT NULL,              -- cifrado
    id_funcao_snapshot UUID NOT NULL,
    funcao_snapshot VARCHAR(512) NOT NULL,            -- cifrado
    id_base_snapshot UUID NOT NULL,
    base_snapshot VARCHAR(120) NOT NULL,
    turno_snapshot VARCHAR(80),

    papel VARCHAR(40) NOT NULL,
    representacao VARCHAR(20),
    titularidade VARCHAR(20),
    origem VARCHAR(20),
    ciclo_eleitoral VARCHAR(120),
    classificacao_eleitoral INTEGER,
    ato_designacao VARCHAR(180),
    reeleito BOOLEAN NOT NULL DEFAULT FALSE,

    inicio_participacao DATE NOT NULL,
    fim_participacao DATE,
    motivo_saida VARCHAR(2048),                       -- cifrado
    data_registro_saida TIMESTAMP WITH TIME ZONE,
    id_usuario_saida UUID,
    id_membro_substituido UUID,
    id_membro_substituto UUID,
    fundamento_substituicao VARCHAR(2048),            -- cifrado
    vacancia_pendente BOOLEAN NOT NULL DEFAULT FALSE,
    conformidade_normativa BOOLEAN NOT NULL DEFAULT TRUE,
    codigo_divergencia_normativa VARCHAR(80),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_membro_corpo FOREIGN KEY (id_corpo)
        REFERENCES corpos_governanca_sst (id),
    CONSTRAINT fk_membro_substituido FOREIGN KEY (id_membro_substituido)
        REFERENCES membros_corpo_sst (id),
    CONSTRAINT fk_membro_substituto FOREIGN KEY (id_membro_substituto)
        REFERENCES membros_corpo_sst (id),
    CONSTRAINT ck_membro_periodo CHECK (
        fim_participacao IS NULL OR fim_participacao >= inicio_participacao),
    CONSTRAINT ck_membro_classificacao CHECK (
        classificacao_eleitoral IS NULL OR classificacao_eleitoral > 0)
);

-- A saída encerra a participação pela data, sem soft-delete do histórico.
CREATE UNIQUE INDEX ux_membro_corpo_participacao_ativa
    ON membros_corpo_sst (id_corpo, id_colaborador)
    WHERE fim_participacao IS NULL AND ativo;

CREATE INDEX ix_membro_corpo_periodo
    ON membros_corpo_sst (id_corpo, inicio_participacao, fim_participacao);
CREATE INDEX ix_membro_corpo_colaborador
    ON membros_corpo_sst (id_colaborador, inicio_participacao DESC);
CREATE INDEX ix_membro_corpo_papel
    ON membros_corpo_sst (id_corpo, papel);
CREATE INDEX ix_membro_corpo_vacancia
    ON membros_corpo_sst (id_corpo, vacancia_pendente)
    WHERE vacancia_pendente;
