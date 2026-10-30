-- Governança documental de CIPA e Brigada.
--
-- Categorias são versionadas porque nomes, metadados e fontes jurisdicionais mudam.
-- O documento aponta para a versão efetivamente usada, preservando o passado.

CREATE TABLE sst_categorias_documento (
    id UUID PRIMARY KEY,
    codigo VARCHAR(80) NOT NULL,
    revisao_categoria INTEGER NOT NULL,
    nome VARCHAR(160) NOT NULL,
    descricao VARCHAR(500),
    contexto VARCHAR(20) NOT NULL,
    natureza VARCHAR(40) NOT NULL,
    escopo_titular VARCHAR(30) NOT NULL,

    jurisdicao VARCHAR(60),
    autoridade_emissora VARCHAR(160),
    identificador_fonte VARCHAR(160),
    edicao_fonte VARCHAR(80),
    url_fonte VARCHAR(500),
    vigencia_inicio DATE,
    vigencia_fim DATE,
    confirmada BOOLEAN NOT NULL DEFAULT FALSE,
    data_confirmacao TIMESTAMP WITH TIME ZONE,
    usuario_confirmacao UUID,

    exige_numero BOOLEAN NOT NULL DEFAULT FALSE,
    exige_emissor BOOLEAN NOT NULL DEFAULT FALSE,
    exige_data_emissao BOOLEAN NOT NULL DEFAULT FALSE,
    exige_data_validade BOOLEAN NOT NULL DEFAULT FALSE,
    sem_validade BOOLEAN NOT NULL DEFAULT FALSE,
    obrigatoria BOOLEAN NOT NULL DEFAULT FALSE,
    retencao_minima_anos INTEGER NOT NULL DEFAULT 0,
    referencia_retencao VARCHAR(160),

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_sst_categoria_documento_revisao UNIQUE (codigo, revisao_categoria),
    CONSTRAINT ck_sst_categoria_documento_contexto
        CHECK (contexto IN ('CIPA', 'BRIGADA', 'AMBOS')),
    CONSTRAINT ck_sst_categoria_documento_natureza
        CHECK (natureza IN ('REGISTRO_CIPA', 'REGISTRO_BRIGADA',
                            'LICENCA_CORPO_BOMBEIROS', 'ATESTADO_BRIGADA',
                            'ALVARA_MUNICIPAL', 'CERTIFICADO_TREINAMENTO')),
    CONSTRAINT ck_sst_categoria_documento_escopo
        CHECK (escopo_titular IN ('BASE', 'CORPO', 'REUNIAO', 'MEMBRO', 'COLABORADOR')),
    CONSTRAINT ck_sst_categoria_documento_datas
        CHECK (vigencia_fim IS NULL OR vigencia_inicio IS NULL OR vigencia_fim >= vigencia_inicio),
    CONSTRAINT ck_sst_categoria_documento_retencao
        CHECK (retencao_minima_anos >= 0),
    CONSTRAINT ck_sst_categoria_documento_confirmacao
        CHECK ((confirmada = FALSE) OR
               (data_confirmacao IS NOT NULL AND usuario_confirmacao IS NOT NULL)),
    CONSTRAINT ck_sst_categoria_documento_validade
        CHECK (NOT (sem_validade AND exige_data_validade)),
    CONSTRAINT ck_sst_categoria_documento_licenca
        CHECK (natureza <> 'LICENCA_CORPO_BOMBEIROS' OR
               (contexto = 'BRIGADA' AND escopo_titular = 'BASE')),
    CONSTRAINT ck_sst_categoria_documento_alvara
        CHECK (natureza <> 'ALVARA_MUNICIPAL' OR escopo_titular = 'BASE'),
    CONSTRAINT ck_sst_categoria_documento_certificado
        CHECK (natureza <> 'CERTIFICADO_TREINAMENTO' OR escopo_titular = 'COLABORADOR')
);

CREATE UNIQUE INDEX ux_sst_categoria_documento_atual
    ON sst_categorias_documento (codigo)
    WHERE ativo;
CREATE INDEX ix_sst_categoria_documento_contexto
    ON sst_categorias_documento (contexto, ativo);
CREATE INDEX ix_sst_categoria_documento_jurisdicao
    ON sst_categorias_documento (jurisdicao, confirmada, ativo);

CREATE TABLE sst_categoria_documento_alertas (
    id_categoria UUID NOT NULL REFERENCES sst_categorias_documento(id),
    dias_antecedencia INTEGER NOT NULL,
    PRIMARY KEY (id_categoria, dias_antecedencia),
    CONSTRAINT ck_sst_categoria_alerta_dias CHECK (dias_antecedencia > 0)
);

CREATE TABLE sst_documentos_governanca (
    id UUID PRIMARY KEY,
    id_categoria UUID NOT NULL REFERENCES sst_categorias_documento(id),
    categoria_codigo VARCHAR(80) NOT NULL,
    categoria_revisao INTEGER NOT NULL,
    id_base UUID NOT NULL,
    contexto VARCHAR(20) NOT NULL,
    escopo_titular VARCHAR(30) NOT NULL,
    id_titular UUID NOT NULL,
    id_colaborador UUID,
    id_treinamento UUID,

    nome_arquivo VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,

    numero_documento VARCHAR(120),
    emissor VARCHAR(200),
    uf CHAR(2),
    referencia_normativa VARCHAR(300),
    data_emissao DATE,
    data_validade DATE,
    antecedencia_alerta_dias INTEGER NOT NULL DEFAULT 0,
    data_inicio_alerta DATE,
    data_conclusao_treinamento DATE,
    carga_horaria_minutos INTEGER,
    modalidade VARCHAR(30),
    observacoes TEXT,

    situacao_conferencia VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    usuario_conferencia UUID,
    data_conferencia TIMESTAMP WITH TIME ZONE,
    motivo_rejeicao TEXT,

    id_documento_substituido UUID REFERENCES sst_documentos_governanca(id),
    id_documento_substituto UUID REFERENCES sst_documentos_governanca(id),
    atual BOOLEAN NOT NULL DEFAULT TRUE,
    data_arquivamento TIMESTAMP WITH TIME ZONE,
    usuario_arquivamento UUID,
    motivo_arquivamento TEXT,
    data_retencao_ate DATE,
    referencia_retencao VARCHAR(160),
    usuario_envio UUID NOT NULL,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_sst_documento_object_key UNIQUE (object_key),
    CONSTRAINT ck_sst_documento_contexto CHECK (contexto IN ('CIPA', 'BRIGADA')),
    CONSTRAINT ck_sst_documento_escopo
        CHECK (escopo_titular IN ('BASE', 'CORPO', 'REUNIAO', 'MEMBRO', 'COLABORADOR')),
    CONSTRAINT ck_sst_documento_conferencia
        CHECK (situacao_conferencia IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_sst_documento_tamanho CHECK (tamanho_bytes > 0),
    CONSTRAINT ck_sst_documento_sha CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_sst_documento_datas
        CHECK (data_validade IS NULL OR data_emissao IS NULL OR data_validade >= data_emissao),
    CONSTRAINT ck_sst_documento_carga
        CHECK (carga_horaria_minutos IS NULL OR carga_horaria_minutos > 0),
    CONSTRAINT ck_sst_documento_antecedencia
        CHECK (antecedencia_alerta_dias >= 0),
    CONSTRAINT ck_sst_documento_rejeicao
        CHECK ((situacao_conferencia <> 'REJECTED') OR
               (motivo_rejeicao IS NOT NULL AND data_conferencia IS NOT NULL
                AND usuario_conferencia IS NOT NULL)),
    CONSTRAINT ck_sst_documento_aprovacao
        CHECK ((situacao_conferencia <> 'APPROVED') OR
               (data_conferencia IS NOT NULL AND usuario_conferencia IS NOT NULL)),
    CONSTRAINT ck_sst_documento_arquivamento
        CHECK ((data_arquivamento IS NULL) OR
               (usuario_arquivamento IS NOT NULL AND motivo_arquivamento IS NOT NULL)),
    CONSTRAINT ck_sst_documento_colaborador
        CHECK ((escopo_titular <> 'COLABORADOR') OR
               (id_colaborador IS NOT NULL AND id_titular = id_colaborador)),
    CONSTRAINT ck_sst_documento_base
        CHECK ((escopo_titular <> 'BASE') OR id_titular = id_base)
);

CREATE UNIQUE INDEX ux_sst_documento_atual_owner
    ON sst_documentos_governanca
       (categoria_codigo, id_base, escopo_titular, id_titular)
    WHERE atual AND ativo AND data_arquivamento IS NULL;
CREATE UNIQUE INDEX ux_sst_documento_hash_owner
    ON sst_documentos_governanca
       (categoria_codigo, id_base, escopo_titular, id_titular, sha256)
    WHERE ativo AND data_arquivamento IS NULL;
CREATE INDEX ix_sst_documento_categoria ON sst_documentos_governanca (id_categoria);
CREATE INDEX ix_sst_documento_base_contexto ON sst_documentos_governanca (id_base, contexto);
CREATE INDEX ix_sst_documento_titular ON sst_documentos_governanca (escopo_titular, id_titular);
CREATE INDEX ix_sst_documento_conferencia ON sst_documentos_governanca (situacao_conferencia);
CREATE INDEX ix_sst_documento_validade ON sst_documentos_governanca (data_validade);
CREATE INDEX ix_sst_documento_inicio_alerta ON sst_documentos_governanca (data_inicio_alerta);
CREATE INDEX ix_sst_documento_colaborador ON sst_documentos_governanca (id_colaborador, id_treinamento);
CREATE INDEX ix_sst_documento_substituido ON sst_documentos_governanca (id_documento_substituido);

CREATE TABLE sst_alertas_documento (
    id UUID PRIMARY KEY,
    id_documento UUID NOT NULL REFERENCES sst_documentos_governanca(id),
    dias_antecedencia INTEGER NOT NULL,
    data_validade DATE NOT NULL,
    data_avaliacao DATE NOT NULL,
    situacao VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    data_superacao TIMESTAMP WITH TIME ZONE,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_sst_alerta_documento_faixa
        UNIQUE (id_documento, dias_antecedencia, data_validade),
    CONSTRAINT ck_sst_alerta_documento_dias CHECK (dias_antecedencia > 0),
    CONSTRAINT ck_sst_alerta_documento_situacao CHECK (situacao IN ('ATIVO', 'SUPERADO'))
);

CREATE INDEX ix_sst_alerta_documento_situacao
    ON sst_alertas_documento (situacao, data_validade);

-- Categorias nacionais e operacionais podem ser usadas imediatamente. Os modelos
-- BA/CBMBA ficam visíveis, mas desconfirmados: não recebem documentos até que uma
-- fonte oficial, edição e vigência sejam conferidas. AVCB/CLCB são exemplos, não
-- um vocabulário universal. Alvará municipal permanece categoria própria.
INSERT INTO sst_categorias_documento (
    id, codigo, revisao_categoria, nome, descricao, contexto, natureza, escopo_titular,
    jurisdicao, autoridade_emissora, identificador_fonte, edicao_fonte, url_fonte,
    vigencia_inicio, confirmada, data_confirmacao, usuario_confirmacao,
    exige_numero, exige_emissor, exige_data_emissao, exige_data_validade, sem_validade,
    obrigatoria, retencao_minima_anos, referencia_retencao,
    versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), v.codigo, 1, v.nome, v.descricao, v.contexto, v.natureza, v.escopo,
       v.jurisdicao, v.autoridade, v.fonte, v.edicao, v.url,
       v.vigencia_inicio, v.confirmada,
       CASE WHEN v.confirmada THEN CURRENT_TIMESTAMP ELSE NULL END,
       CASE WHEN v.confirmada THEN '00000000-0000-0000-0000-000000000001'::uuid ELSE NULL END,
       v.exige_numero, v.exige_emissor, v.exige_emissao, v.exige_validade, v.sem_validade,
       v.obrigatoria, v.retencao_anos, v.referencia_retencao,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM (VALUES
    ('CIPA_PROCESSO_ELEITORAL', 'Processo eleitoral da CIPA',
     'Editais, inscrições, votação, apuração e resultado do processo eleitoral.',
     'CIPA', 'REGISTRO_CIPA', 'CORPO', 'BR', 'MTE', 'NR-5', '2023',
     'https://www.gov.br/trabalho-e-emprego/', DATE '2023-03-20', TRUE,
     FALSE, FALSE, TRUE, FALSE, TRUE, FALSE, 5, 'NR-5'),
    ('CIPA_ATA_REUNIAO', 'Ata de reunião da CIPA',
     'Cópia assinada e demais evidências documentais da reunião.',
     'CIPA', 'REGISTRO_CIPA', 'REUNIAO', 'BR', 'MTE', 'NR-5', '2023',
     'https://www.gov.br/trabalho-e-emprego/', DATE '2023-03-20', TRUE,
     FALSE, FALSE, TRUE, FALSE, TRUE, TRUE, 5, 'NR-5'),
    ('CIPA_CERTIFICADO_TREINAMENTO', 'Certificado de treinamento da CIPA',
     'Evidência individual de formação ou atualização relacionada à CIPA.',
     'CIPA', 'CERTIFICADO_TREINAMENTO', 'COLABORADOR', 'BR', 'MTE', 'NR-5', '2023',
     'https://www.gov.br/trabalho-e-emprego/', DATE '2023-03-20', TRUE,
     FALSE, TRUE, TRUE, FALSE, FALSE, TRUE, 5, 'NR-5'),
    ('BRIGADA_COMPOSICAO', 'Documento de composição da Brigada',
     'Relação, designação ou documento equivalente de composição da Brigada.',
     'BRIGADA', 'REGISTRO_BRIGADA', 'CORPO', NULL, NULL, NULL, NULL,
     NULL, NULL, TRUE,
     FALSE, FALSE, TRUE, FALSE, TRUE, FALSE, 0, NULL),
    ('BRIGADA_ATESTADO', 'Atestado da Brigada',
     'Atestado da Brigada, distinto da licença da edificação e dos certificados individuais.',
     'BRIGADA', 'ATESTADO_BRIGADA', 'CORPO', NULL, NULL, NULL, NULL,
     NULL, NULL, TRUE,
     TRUE, TRUE, TRUE, FALSE, FALSE, FALSE, 0, NULL),
    ('BRIGADA_CERTIFICADO_TREINAMENTO', 'Certificado de treinamento de brigadista',
     'Evidência individual de formação ou atualização do brigadista.',
     'BRIGADA', 'CERTIFICADO_TREINAMENTO', 'COLABORADOR', NULL, NULL, NULL, NULL,
     NULL, NULL, TRUE,
     FALSE, TRUE, TRUE, FALSE, FALSE, FALSE, 0, NULL),
    ('BA_CBMBA_AVCB_MODELO', 'AVCB — modelo a confirmar para BA/CBMBA',
     'Exemplo de licença ou ato da edificação; requer confirmação da fonte oficial aplicável.',
     'BRIGADA', 'LICENCA_CORPO_BOMBEIROS', 'BASE', 'BA/CBMBA', 'CBMBA', NULL, NULL,
     NULL, NULL, FALSE,
     TRUE, TRUE, TRUE, TRUE, FALSE, FALSE, 0, NULL),
    ('BA_CBMBA_CLCB_MODELO', 'CLCB — modelo a confirmar para BA/CBMBA',
     'Exemplo de licença ou ato da edificação; requer confirmação da fonte oficial aplicável.',
     'BRIGADA', 'LICENCA_CORPO_BOMBEIROS', 'BASE', 'BA/CBMBA', 'CBMBA', NULL, NULL,
     NULL, NULL, FALSE,
     TRUE, TRUE, TRUE, TRUE, FALSE, FALSE, 0, NULL),
    ('ALVARA_MUNICIPAL', 'Alvará municipal',
     'Documento municipal separado de qualquer licença ou ato emitido pelo Corpo de Bombeiros.',
     'BRIGADA', 'ALVARA_MUNICIPAL', 'BASE', 'BA/MUNICIPAL', 'MUNICÍPIO A CONFIRMAR', NULL, NULL,
     NULL, NULL, FALSE,
     TRUE, TRUE, TRUE, TRUE, FALSE, FALSE, 0, NULL)
) AS v(codigo, nome, descricao, contexto, natureza, escopo,
       jurisdicao, autoridade, fonte, edicao, url, vigencia_inicio, confirmada,
       exige_numero, exige_emissor, exige_emissao, exige_validade, sem_validade,
       obrigatoria, retencao_anos, referencia_retencao)
ON CONFLICT (codigo, revisao_categoria) DO NOTHING;

INSERT INTO sst_categoria_documento_alertas (id_categoria, dias_antecedencia)
SELECT c.id, d.dias
FROM sst_categorias_documento c
CROSS JOIN (VALUES (90), (30)) AS d(dias)
WHERE c.codigo IN ('BA_CBMBA_AVCB_MODELO', 'BA_CBMBA_CLCB_MODELO', 'ALVARA_MUNICIPAL')
ON CONFLICT DO NOTHING;
