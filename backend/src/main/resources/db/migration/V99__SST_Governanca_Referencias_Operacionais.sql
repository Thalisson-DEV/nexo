-- Extensões aditivas incorporadas a partir das referências operacionais da empresa.
-- Dados históricos de matriz/escolas/equipamentos serão carregados por importador
-- com conferência humana; esta migration não os transforma em regra vigente.

CREATE TABLE normalizacoes_transcricao_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    id_revisao_transcricao UUID NOT NULL REFERENCES revisoes_transcricao_sst(id),
    numero INTEGER NOT NULL,
    client_request_id UUID NOT NULL,
    hash_insumos CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    conteudo_estruturado TEXT,
    sha256_conteudo CHAR(64),
    versao_modelo VARCHAR(80) NOT NULL,
    id_solicitante UUID NOT NULL,
    codigo_falha VARCHAR(60),
    concluida_em TIMESTAMP WITH TIME ZONE,
    confirmada_em TIMESTAMP WITH TIME ZONE,
    id_usuario_confirmacao UUID,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_normalizacao_transcricao_numero UNIQUE (id_reuniao, numero),
    CONSTRAINT ux_normalizacao_transcricao_cliente UNIQUE (id_reuniao, client_request_id),
    CONSTRAINT ck_normalizacao_transcricao_numero CHECK (numero > 0),
    CONSTRAINT ck_normalizacao_transcricao_hash_insumos CHECK (hash_insumos ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_normalizacao_transcricao_status CHECK (status IN
        ('EM_PROCESSAMENTO', 'RASCUNHO', 'CONFIRMADA', 'FALHA')),
    CONSTRAINT ck_normalizacao_transcricao_resultado CHECK (
        (status = 'EM_PROCESSAMENTO' AND conteudo_estruturado IS NULL AND sha256_conteudo IS NULL
            AND codigo_falha IS NULL AND concluida_em IS NULL)
        OR (status = 'RASCUNHO' AND conteudo_estruturado IS NOT NULL
            AND sha256_conteudo ~ '^[0-9a-f]{64}$' AND codigo_falha IS NULL AND concluida_em IS NOT NULL)
        OR (status = 'CONFIRMADA' AND conteudo_estruturado IS NOT NULL
            AND sha256_conteudo ~ '^[0-9a-f]{64}$' AND codigo_falha IS NULL
            AND concluida_em IS NOT NULL AND confirmada_em IS NOT NULL AND id_usuario_confirmacao IS NOT NULL)
        OR (status = 'FALHA' AND conteudo_estruturado IS NULL AND sha256_conteudo IS NULL
            AND codigo_falha IS NOT NULL AND concluida_em IS NOT NULL))
);

CREATE INDEX ix_normalizacao_transcricao_reuniao
    ON normalizacoes_transcricao_sst (id_reuniao, numero DESC, status);

-- Bases DOCX configuráveis e artefatos gerados. O binário permanece no storage
-- privado; a base publicada é identificada por hash e nunca é sobrescrita.
CREATE TABLE arquivos_modelo_ata_sst (
    id UUID PRIMARY KEY,
    id_modelo UUID NOT NULL REFERENCES modelos_ata_sst(id),
    nome_arquivo VARCHAR(1024) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    mapeamento_slots TEXT,
    validado_em TIMESTAMP WITH TIME ZONE,
    id_usuario_validacao UUID,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_arquivo_modelo_ata_hash UNIQUE (id_modelo, sha256),
    CONSTRAINT ck_arquivo_modelo_ata_tamanho CHECK (tamanho_bytes > 0),
    CONSTRAINT ck_arquivo_modelo_ata_tipo CHECK (
        content_type = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'),
    CONSTRAINT ck_arquivo_modelo_ata_hash CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

ALTER TABLE modelos_ata_sst ADD COLUMN id_arquivo_docx UUID REFERENCES arquivos_modelo_ata_sst(id);
ALTER TABLE modelos_ata_sst ADD COLUMN hash_arquivo_docx CHAR(64);

CREATE TABLE arquivos_ata_gerada_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    id_versao_ata UUID NOT NULL REFERENCES versoes_ata_sst(id),
    id_modelo_ata UUID NOT NULL REFERENCES modelos_ata_sst(id),
    revisao_modelo_ata INTEGER NOT NULL,
    hash_modelo_docx CHAR(64) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    id_usuario_geracao UUID NOT NULL,
    gerado_em TIMESTAMP WITH TIME ZONE NOT NULL,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_arquivo_ata_gerada UNIQUE (id_versao_ata, id_modelo_ata, revisao_modelo_ata),
    CONSTRAINT ck_arquivo_ata_gerada_tamanho CHECK (tamanho_bytes > 0),
    CONSTRAINT ck_arquivo_ata_gerada_tipo CHECK (
        content_type = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'),
    CONSTRAINT ck_arquivo_ata_gerada_hash CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_arquivo_ata_gerada_hash_modelo CHECK (hash_modelo_docx ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_arquivo_ata_gerada_reuniao
    ON arquivos_ata_gerada_sst (id_reuniao, gerado_em DESC);

CREATE TABLE processos_treinamento_sst (
    id UUID PRIMARY KEY,
    codigo VARCHAR(80) NOT NULL,
    nome VARCHAR(180) NOT NULL,
    versao_referencia INTEGER NOT NULL DEFAULT 1,
    fonte VARCHAR(255),
    vigente_desde DATE,
    confirmado BOOLEAN NOT NULL DEFAULT FALSE,
    id_usuario_confirmacao UUID,
    confirmado_em TIMESTAMP WITH TIME ZONE,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ux_processo_treinamento_codigo_versao UNIQUE (codigo, versao_referencia)
);

ALTER TABLE sst_exigencias_treinamento ADD COLUMN id_processo UUID REFERENCES processos_treinamento_sst(id);
ALTER TABLE sst_exigencias_treinamento ADD COLUMN valor_fonte VARCHAR(80);
ALTER TABLE sst_exigencias_treinamento ADD COLUMN marcador_fonte VARCHAR(20);
ALTER TABLE sst_exigencias_treinamento ADD COLUMN status_interpretacao VARCHAR(30) DEFAULT 'CONFIRMADA';
ALTER TABLE sst_exigencias_treinamento ADD CONSTRAINT ck_exigencia_status_interpretacao
    CHECK (status_interpretacao IN ('CONFIRMADA', 'PENDENTE_INTERPRETACAO'));

CREATE TABLE condicoes_exigencia_treinamento_sst (
    id UUID PRIMARY KEY,
    id_exigencia UUID NOT NULL REFERENCES sst_exigencias_treinamento(id),
    codigo VARCHAR(80) NOT NULL,
    pergunta VARCHAR(500) NOT NULL,
    valor_que_ativa VARCHAR(120) NOT NULL,
    valor_fonte VARCHAR(80),
    confirmada BOOLEAN NOT NULL DEFAULT FALSE,
    versao_regra INTEGER NOT NULL DEFAULT 1,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ux_condicao_exigencia_codigo UNIQUE (id_exigencia, codigo, versao_regra)
);

CREATE TABLE respostas_contexto_treinamento_sst (
    id UUID PRIMARY KEY,
    id_condicao UUID NOT NULL REFERENCES condicoes_exigencia_treinamento_sst(id),
    id_colaborador UUID NOT NULL,
    resposta VARCHAR(120) NOT NULL,
    valida_de DATE NOT NULL,
    valida_ate DATE,
    id_usuario_confirmacao UUID NOT NULL,
    confirmada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE instituicoes_treinamento_sst (
    id UUID PRIMARY KEY,
    cnpj VARCHAR(20),
    nome VARCHAR(200) NOT NULL,
    razao_social VARCHAR(240),
    parceria_instituicao VARCHAR(240),
    inscricao_municipal_literal VARCHAR(120),
    local VARCHAR(240),
    telefones TEXT,
    contatos TEXT,
    fonte_referencia VARCHAR(255),
    data_referencia DATE,
    possui_dado_suspeito BOOLEAN NOT NULL DEFAULT FALSE,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX ux_instituicao_treinamento_cnpj
    ON instituicoes_treinamento_sst (cnpj) WHERE cnpj IS NOT NULL AND ativo;

CREATE TABLE homologacoes_instituicao_sst (
    id UUID PRIMARY KEY,
    id_instituicao UUID NOT NULL REFERENCES instituicoes_treinamento_sst(id),
    status VARCHAR(30) NOT NULL,
    status_fonte VARCHAR(40) NOT NULL,
    inicio_literal VARCHAR(40),
    fim_literal VARCHAR(40),
    vigencia_inicio DATE,
    vigencia_fim DATE,
    escopo VARCHAR(240),
    ead_liberado BOOLEAN,
    ead_literal VARCHAR(80),
    competencia_liberada BOOLEAN,
    competencia_literal VARCHAR(80),
    id_documento_evidencia UUID REFERENCES sst_documentos_governanca(id),
    id_usuario_confirmacao UUID,
    confirmada_em TIMESTAMP WITH TIME ZONE,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_homologacao_instituicao_status CHECK (status IN
        ('PENDENTE_CONFIRMACAO', 'HOMOLOGADA', 'DESCREDENCIADA'))
);
CREATE INDEX ix_homologacao_instituicao_vigencia
    ON homologacoes_instituicao_sst (id_instituicao, vigencia_inicio, vigencia_fim, status);

CREATE TABLE ativos_inspecionaveis_sst (
    id UUID PRIMARY KEY,
    id_base_operacional UUID NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    identificador_local VARCHAR(80) NOT NULL,
    localizacao VARCHAR(300) NOT NULL,
    atributos TEXT,
    origem VARCHAR(40) NOT NULL,
    status_conferencia VARCHAR(30) NOT NULL,
    possui_dado_suspeito BOOLEAN NOT NULL DEFAULT FALSE,
    inicio_vigencia DATE NOT NULL,
    fim_vigencia DATE,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ux_ativo_inspecionavel_local UNIQUE (id_base_operacional, tipo, identificador_local),
    CONSTRAINT ck_ativo_inspecionavel_tipo CHECK (tipo IN ('LUZ_EMERGENCIA', 'EXTINTOR', 'OUTRO')),
    CONSTRAINT ck_ativo_inspecionavel_status CHECK (status_conferencia IN
        ('RASCUNHO', 'CONFIRMADO', 'REJEITADO'))
);
CREATE INDEX ix_ativo_inspecionavel_base_tipo
    ON ativos_inspecionaveis_sst (id_base_operacional, tipo, ativo);

CREATE TABLE importacoes_referencia_governanca_sst (
    id UUID PRIMARY KEY,
    tipo VARCHAR(50) NOT NULL,
    sha256_fonte CHAR(64) NOT NULL,
    id_base_operacional UUID,
    quantidade_registros INTEGER NOT NULL,
    resultado VARCHAR(30) NOT NULL,
    id_usuario UUID NOT NULL,
    importada_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_importacao_referencia UNIQUE (tipo, sha256_fonte, id_base_operacional),
    CONSTRAINT ck_importacao_resultado CHECK (resultado IN ('IMPORTADO', 'JA_EXISTENTE', 'FALHA'))
);

CREATE TABLE referencias_matriz_treinamento_sst (
    id UUID PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    processo_literal VARCHAR(240) NOT NULL,
    funcao_literal VARCHAR(240) NOT NULL,
    requisitos_literal TEXT NOT NULL,
    total_horas_fonte INTEGER,
    sha256_fonte CHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDENTE_ENQUADRAMENTO',
    id_funcao_rh UUID,
    id_processo UUID REFERENCES processos_treinamento_sst(id),
    id_usuario_enquadramento UUID,
    enquadrada_em TIMESTAMP WITH TIME ZONE,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ux_referencia_matriz_linha UNIQUE (sha256_fonte, tipo, processo_literal, funcao_literal),
    CONSTRAINT ck_referencia_matriz_tipo CHECK (tipo IN ('PRINCIPAL', 'POP')),
    CONSTRAINT ck_referencia_matriz_status CHECK (status IN
        ('PENDENTE_ENQUADRAMENTO', 'ENQUADRADA', 'REJEITADA'))
);

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:governanca:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:governanca:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:governanca:aprovar'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:documento:conferir'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:ata:generate'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:treinamento:homologar')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id FROM perfis_acesso p CROSS JOIN permissoes perm
WHERE p.nome IN ('ADMINISTRADOR', 'COORDENADOR_SESMT')
  AND perm.nome IN ('sst:governanca:read', 'sst:governanca:write', 'sst:governanca:aprovar',
                    'sst:documento:conferir', 'sst:ata:generate', 'sst:treinamento:homologar')
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id FROM perfis_acesso p CROSS JOIN permissoes perm
WHERE p.nome = 'BACKOFFICE_SESMT'
  AND perm.nome IN ('sst:governanca:read', 'sst:governanca:write', 'sst:ata:generate')
ON CONFLICT DO NOTHING;
