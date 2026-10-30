-- Reuniões, presenças, transcrições, atas e anexos privados da governança SST.
-- Conteúdo livre protegido por CryptoConverterImpl é armazenado em TEXT; nenhum
-- texto ou binário é copiado para tabelas analíticas por esta migration.

CREATE TABLE modelos_ata_sst (
    id UUID PRIMARY KEY,
    familia_id UUID NOT NULL,
    revisao INTEGER NOT NULL,
    id_revisao_anterior UUID REFERENCES modelos_ata_sst(id),
    id_modelo_duplicado UUID REFERENCES modelos_ata_sst(id),
    tipo_corpo VARCHAR(20) NOT NULL,
    id_base_aplicavel UUID,
    nome VARCHAR(160) NOT NULL,
    orientacoes_gerais VARCHAR(2000),
    status VARCHAR(20) NOT NULL,
    modelo_inicial BOOLEAN NOT NULL DEFAULT FALSE,
    publicado_em TIMESTAMP WITH TIME ZONE,
    id_usuario_publicacao UUID,
    retirado_em TIMESTAMP WITH TIME ZONE,
    id_usuario_retirada UUID,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_modelo_ata_familia_revisao UNIQUE (familia_id, revisao),
    CONSTRAINT ck_modelo_ata_revisao CHECK (revisao > 0),
    CONSTRAINT ck_modelo_ata_tipo CHECK (tipo_corpo IN ('CIPA', 'BRIGADA')),
    CONSTRAINT ck_modelo_ata_status CHECK (status IN ('RASCUNHO', 'PUBLICADO', 'RETIRADO')),
    CONSTRAINT ck_modelo_ata_publicacao CHECK (
        status = 'RASCUNHO' OR publicado_em IS NOT NULL),
    CONSTRAINT ck_modelo_ata_retirada CHECK (
        status <> 'RETIRADO' OR (retirado_em IS NOT NULL AND id_usuario_retirada IS NOT NULL))
);

CREATE INDEX ix_modelo_ata_aplicabilidade
    ON modelos_ata_sst (tipo_corpo, id_base_aplicavel, status, ativo);

CREATE TABLE secoes_modelo_ata_sst (
    id UUID PRIMARY KEY,
    id_modelo UUID NOT NULL REFERENCES modelos_ata_sst(id),
    codigo VARCHAR(50) NOT NULL,
    ordem INTEGER NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    orientacao VARCHAR(1000),
    obrigatoria BOOLEAN NOT NULL,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_secao_modelo_ata_ordem UNIQUE (id_modelo, ordem),
    CONSTRAINT ck_secao_modelo_ata_ordem CHECK (ordem > 0),
    CONSTRAINT ck_secao_modelo_ata_codigo CHECK (codigo IN
        ('IDENTIFICACAO_REUNIAO', 'DATA_E_LOCAL', 'PARTICIPANTES_E_PRESENCAS',
         'PAUTA', 'DELIBERACOES', 'ASSINATURAS', 'PERSONALIZADA'))
);

CREATE TABLE reunioes_governanca_sst (
    id UUID PRIMARY KEY,
    id_corpo UUID NOT NULL REFERENCES corpos_governanca_sst(id),
    id_base_operacional UUID NOT NULL,
    tipo_corpo VARCHAR(20) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    data_hora TIMESTAMP WITH TIME ZONE NOT NULL,
    local_ou_meio VARCHAR(255) NOT NULL,
    pauta TEXT NOT NULL,
    id_responsavel UUID NOT NULL,
    id_modelo_ata UUID NOT NULL REFERENCES modelos_ata_sst(id),
    revisao_modelo_ata INTEGER NOT NULL,
    snapshot_modelo_ata TEXT NOT NULL,
    registro_historico BOOLEAN NOT NULL DEFAULT FALSE,
    justificativa_historico TEXT,
    motivo_extraordinaria TEXT,
    data_fato_gerador TIMESTAMP WITH TIME ZONE,
    origem_extraordinaria VARCHAR(120),
    tipo_secretario VARCHAR(20),
    id_secretario_participacao UUID,
    id_secretario_convidado UUID,
    iniciada_em TIMESTAMP WITH TIME ZONE,
    presencas_concluidas_em TIMESTAMP WITH TIME ZONE,
    id_versao_ata_aprovada UUID,
    finalizada_em TIMESTAMP WITH TIME ZONE,
    id_usuario_finalizacao UUID,
    motivo_reabertura TEXT,
    reaberta_em TIMESTAMP WITH TIME ZONE,
    id_usuario_reabertura UUID,
    motivo_cancelamento TEXT,
    cancelada_em TIMESTAMP WITH TIME ZONE,
    id_usuario_cancelamento UUID,
    proxima_revisao_transcricao INTEGER NOT NULL DEFAULT 0,
    proxima_versao_ata INTEGER NOT NULL DEFAULT 0,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_reuniao_governanca_tipo_corpo CHECK (tipo_corpo IN ('CIPA', 'BRIGADA')),
    CONSTRAINT ck_reuniao_governanca_tipo CHECK (tipo IN ('ORDINARIA', 'EXTRAORDINARIA', 'SIMULADO', 'OUTRO')),
    CONSTRAINT ck_reuniao_governanca_status CHECK (status IN
        ('AGENDADA', 'EM_REALIZACAO', 'AGUARDANDO_ATA', 'AGUARDANDO_ASSINATURAS', 'FINALIZADA', 'CANCELADA')),
    CONSTRAINT ck_reuniao_governanca_secretario CHECK (
        (tipo_secretario IS NULL AND id_secretario_participacao IS NULL AND id_secretario_convidado IS NULL)
        OR (tipo_secretario = 'PARTICIPANTE' AND id_secretario_participacao IS NOT NULL AND id_secretario_convidado IS NULL)
        OR (tipo_secretario = 'CONVIDADO' AND id_secretario_convidado IS NOT NULL AND id_secretario_participacao IS NULL)),
    CONSTRAINT ck_reuniao_governanca_extraordinaria CHECK (
        tipo <> 'EXTRAORDINARIA'
        OR (motivo_extraordinaria IS NOT NULL AND data_fato_gerador IS NOT NULL AND origem_extraordinaria IS NOT NULL)),
    CONSTRAINT ck_reuniao_governanca_historico CHECK (
        registro_historico = FALSE OR justificativa_historico IS NOT NULL),
    CONSTRAINT ck_reuniao_governanca_finalizacao CHECK (
        status <> 'FINALIZADA' OR (finalizada_em IS NOT NULL AND id_usuario_finalizacao IS NOT NULL)),
    CONSTRAINT ck_reuniao_governanca_cancelamento CHECK (
        status <> 'CANCELADA' OR (cancelada_em IS NOT NULL AND id_usuario_cancelamento IS NOT NULL AND motivo_cancelamento IS NOT NULL))
);

CREATE INDEX ix_reuniao_governanca_agenda ON reunioes_governanca_sst (data_hora, status);
CREATE INDEX ix_reuniao_governanca_corpo ON reunioes_governanca_sst (id_corpo, data_hora);
CREATE INDEX ix_reuniao_governanca_base ON reunioes_governanca_sst (id_base_operacional, tipo_corpo, status);

CREATE TABLE participacoes_reuniao_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    id_membro_corpo UUID NOT NULL REFERENCES membros_corpo_sst(id),
    id_colaborador UUID NOT NULL,
    nome_snapshot VARCHAR(1024) NOT NULL,
    funcao_snapshot VARCHAR(160),
    papel_snapshot VARCHAR(60) NOT NULL,
    representacao_cipa_snapshot VARCHAR(30),
    titularidade_cipa_snapshot VARCHAR(30),
    turno_snapshot VARCHAR(80),
    obrigatorio BOOLEAN NOT NULL DEFAULT TRUE,
    presenca VARCHAR(40) NOT NULL DEFAULT 'NAO_REGISTRADA',
    justificativa_ausencia TEXT,
    id_usuario_classificacao UUID,
    classificada_em TIMESTAMP WITH TIME ZONE,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_participacao_reuniao_membro UNIQUE (id_reuniao, id_membro_corpo),
    CONSTRAINT ck_participacao_reuniao_presenca CHECK (presenca IN
        ('PRESENTE', 'AUSENTE_JUSTIFICADA', 'AUSENTE_NAO_JUSTIFICADA', 'NAO_REGISTRADA')),
    CONSTRAINT ck_participacao_reuniao_justificativa CHECK (
        presenca <> 'AUSENTE_JUSTIFICADA' OR justificativa_ausencia IS NOT NULL),
    CONSTRAINT ck_participacao_reuniao_classificacao CHECK (
        presenca = 'NAO_REGISTRADA' OR (id_usuario_classificacao IS NOT NULL AND classificada_em IS NOT NULL))
);

CREATE INDEX ix_participacao_reuniao_presenca ON participacoes_reuniao_sst (id_reuniao, presenca);
CREATE INDEX ix_participacao_faltas_cipa ON participacoes_reuniao_sst
    (id_membro_corpo, titularidade_cipa_snapshot, presenca);

CREATE TABLE convidados_reuniao_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    id_colaborador UUID,
    nome_snapshot VARCHAR(1024) NOT NULL,
    funcao_snapshot VARCHAR(160),
    organizacao_snapshot VARCHAR(180),
    presente BOOLEAN NOT NULL DEFAULT FALSE,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_convidado_reuniao ON convidados_reuniao_sst (id_reuniao);

CREATE TABLE revisoes_transcricao_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    numero INTEGER NOT NULL,
    origem VARCHAR(30) NOT NULL,
    conteudo TEXT NOT NULL,
    hash_conteudo CHAR(64) NOT NULL,
    confirmada_em TIMESTAMP WITH TIME ZONE,
    id_usuario_confirmacao UUID,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_revisao_transcricao_numero UNIQUE (id_reuniao, numero),
    CONSTRAINT ck_revisao_transcricao_origem CHECK (origem IN ('DITADO_NAVEGADOR', 'TEXTO_COLADO', 'DIGITADO')),
    CONSTRAINT ck_revisao_transcricao_hash CHECK (hash_conteudo ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_revisao_transcricao_confirmacao CHECK (
        confirmada_em IS NULL OR id_usuario_confirmacao IS NOT NULL)
);

CREATE TABLE versoes_ata_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    numero INTEGER NOT NULL,
    origem VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    conteudo TEXT NOT NULL,
    sha256_conteudo CHAR(64) NOT NULL,
    id_versao_base UUID REFERENCES versoes_ata_sst(id),
    id_revisao_transcricao UUID REFERENCES revisoes_transcricao_sst(id),
    id_modelo_ata UUID NOT NULL REFERENCES modelos_ata_sst(id),
    revisao_modelo_ata INTEGER NOT NULL,
    snapshot_modelo_ata TEXT NOT NULL,
    instrucao_correcao TEXT,
    versao_prompt VARCHAR(40),
    id_autor_ou_solicitante UUID NOT NULL,
    aprovada_em TIMESTAMP WITH TIME ZONE,
    id_usuario_aprovacao UUID,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_versao_ata_numero UNIQUE (id_reuniao, numero),
    CONSTRAINT ck_versao_ata_origem CHECK (origem IN ('MANUAL', 'IA')),
    CONSTRAINT ck_versao_ata_status CHECK (status IN ('RASCUNHO', 'APROVADA')),
    CONSTRAINT ck_versao_ata_hash CHECK (sha256_conteudo ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_versao_ata_ia_transcricao CHECK (origem <> 'IA' OR id_revisao_transcricao IS NOT NULL),
    CONSTRAINT ck_versao_ata_aprovacao CHECK (
        status <> 'APROVADA' OR (aprovada_em IS NOT NULL AND id_usuario_aprovacao IS NOT NULL))
);

ALTER TABLE reunioes_governanca_sst
    ADD CONSTRAINT fk_reuniao_versao_ata_aprovada
    FOREIGN KEY (id_versao_ata_aprovada) REFERENCES versoes_ata_sst(id);

CREATE INDEX ix_versao_ata_reuniao ON versoes_ata_sst (id_reuniao, numero DESC);

CREATE TABLE tentativas_geracao_ata_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    client_request_id UUID NOT NULL,
    hash_insumos CHAR(64) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    id_versao_base UUID REFERENCES versoes_ata_sst(id),
    id_revisao_transcricao UUID NOT NULL REFERENCES revisoes_transcricao_sst(id),
    id_solicitante UUID NOT NULL,
    id_versao_gerada UUID REFERENCES versoes_ata_sst(id),
    codigo_falha VARCHAR(60),
    iniciada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    concluida_em TIMESTAMP WITH TIME ZONE,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_tentativa_geracao_cliente UNIQUE (id_reuniao, client_request_id),
    CONSTRAINT ck_tentativa_geracao_hash CHECK (hash_insumos ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_tentativa_geracao_tipo CHECK (tipo IN ('GERACAO', 'CORRECAO')),
    CONSTRAINT ck_tentativa_geracao_status CHECK (status IN ('EM_PROCESSAMENTO', 'CONCLUIDA', 'FALHA')),
    CONSTRAINT ck_tentativa_geracao_resultado CHECK (
        (status = 'EM_PROCESSAMENTO' AND concluida_em IS NULL AND id_versao_gerada IS NULL AND codigo_falha IS NULL)
        OR (status = 'CONCLUIDA' AND concluida_em IS NOT NULL AND id_versao_gerada IS NOT NULL AND codigo_falha IS NULL)
        OR (status = 'FALHA' AND concluida_em IS NOT NULL AND id_versao_gerada IS NULL AND codigo_falha IS NOT NULL))
);

CREATE TABLE copias_assinadas_ata_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    id_versao_ata UUID NOT NULL REFERENCES versoes_ata_sst(id),
    nome_arquivo VARCHAR(1024) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    id_usuario_upload UUID NOT NULL,
    enviada_em TIMESTAMP WITH TIME ZONE NOT NULL,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_copia_assinada_versao UNIQUE (id_reuniao, id_versao_ata),
    CONSTRAINT ux_copia_assinada_object_key UNIQUE (object_key),
    CONSTRAINT ck_copia_assinada_tamanho CHECK (tamanho_bytes > 0),
    CONSTRAINT ck_copia_assinada_hash CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE evidencias_fotograficas_reuniao_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    nome_arquivo VARCHAR(1024) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    legenda VARCHAR(2048),
    id_autor UUID NOT NULL,
    enviada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    removida_em TIMESTAMP WITH TIME ZONE,
    id_usuario_remocao UUID,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_evidencia_foto_reuniao_object_key UNIQUE (object_key),
    CONSTRAINT ck_evidencia_foto_reuniao_tipo CHECK (content_type IN
        ('image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/heif')),
    CONSTRAINT ck_evidencia_foto_reuniao_tamanho CHECK (tamanho_bytes > 0),
    CONSTRAINT ck_evidencia_foto_reuniao_hash CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_evidencia_foto_reuniao_remocao CHECK (
        removida_em IS NULL OR id_usuario_remocao IS NOT NULL)
);

CREATE INDEX ix_evidencia_foto_reuniao ON evidencias_fotograficas_reuniao_sst (id_reuniao, ativo);

CREATE TABLE comunicacoes_ata_cipa_sst (
    id UUID PRIMARY KEY,
    id_reuniao UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    id_versao_ata UUID NOT NULL REFERENCES versoes_ata_sst(id),
    tipo VARCHAR(60) NOT NULL,
    comunicada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    canal VARCHAR(1024) NOT NULL,
    alcance VARCHAR(2048),
    id_responsavel UUID NOT NULL,
    nome_evidencia VARCHAR(1024),
    content_type_evidencia VARCHAR(120),
    tamanho_evidencia BIGINT,
    object_key_evidencia VARCHAR(500),
    sha256_evidencia CHAR(64),

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_comunicacao_ata_cipa_tipo CHECK (tipo IN
        ('DISPONIBILIZACAO_AOS_MEMBROS', 'ENCAMINHAMENTOS_AOS_TRABALHADORES')),
    CONSTRAINT ck_comunicacao_ata_cipa_alcance CHECK (
        tipo <> 'ENCAMINHAMENTOS_AOS_TRABALHADORES' OR alcance IS NOT NULL),
    CONSTRAINT ck_comunicacao_ata_cipa_evidencia CHECK (
        (object_key_evidencia IS NULL AND nome_evidencia IS NULL AND content_type_evidencia IS NULL
            AND tamanho_evidencia IS NULL AND sha256_evidencia IS NULL)
        OR (object_key_evidencia IS NOT NULL AND nome_evidencia IS NOT NULL
            AND content_type_evidencia IS NOT NULL AND tamanho_evidencia > 0
            AND sha256_evidencia ~ '^[0-9a-f]{64}$')),
    CONSTRAINT ux_comunicacao_ata_cipa_object_key UNIQUE (object_key_evidencia)
);

CREATE INDEX ix_comunicacao_ata_cipa_reuniao ON comunicacoes_ata_cipa_sst (id_reuniao, comunicada_em DESC);

CREATE TABLE alertas_falta_cipa_sst (
    id UUID PRIMARY KEY,
    id_corpo UUID NOT NULL REFERENCES corpos_governanca_sst(id),
    id_membro_corpo UUID NOT NULL REFERENCES membros_corpo_sst(id),
    id_reuniao_gatilho UUID NOT NULL REFERENCES reunioes_governanca_sst(id),
    quantidade_faltas INTEGER NOT NULL,
    emitido_em TIMESTAMP WITH TIME ZONE NOT NULL,
    conferido_em TIMESTAMP WITH TIME ZONE,
    id_usuario_conferencia UUID,

    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ux_alerta_falta_cipa_mandato_membro UNIQUE (id_corpo, id_membro_corpo),
    CONSTRAINT ck_alerta_falta_cipa_quantidade CHECK (quantidade_faltas >= 5),
    CONSTRAINT ck_alerta_falta_cipa_conferencia CHECK (
        conferido_em IS NULL OR id_usuario_conferencia IS NOT NULL)
);

CREATE INDEX ix_alerta_falta_cipa_pendente ON alertas_falta_cipa_sst (id_corpo, conferido_em);

-- Modelos iniciais simples: podem ser usados imediatamente e duplicados no editor.
INSERT INTO modelos_ata_sst (
    id, familia_id, revisao, tipo_corpo, nome, orientacoes_gerais, status,
    modelo_inicial, publicado_em, id_usuario_publicacao,
    versao, data_criacao, usuario_criacao, ativo)
VALUES
    ('51000000-0000-0000-0000-000000000001', '51000000-0000-0000-0000-000000000011', 1,
     'CIPA', 'Ata simples da CIPA', 'Use linguagem objetiva e registre somente fatos confirmados.',
     'PUBLICADO', TRUE, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000001',
     0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('51000000-0000-0000-0000-000000000002', '51000000-0000-0000-0000-000000000012', 1,
     'BRIGADA', 'Ata simples da Brigada', 'Use linguagem objetiva e não declare conformidade automaticamente.',
     'PUBLICADO', TRUE, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000001',
     0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE);

INSERT INTO secoes_modelo_ata_sst (
    id, id_modelo, codigo, ordem, titulo, orientacao, obrigatoria,
    versao, data_criacao, usuario_criacao, ativo)
VALUES
    ('52000000-0000-0000-0000-000000000001', '51000000-0000-0000-0000-000000000001', 'IDENTIFICACAO_REUNIAO', 1, 'Identificação da reunião', NULL, TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000002', '51000000-0000-0000-0000-000000000001', 'DATA_E_LOCAL', 2, 'Data e local', NULL, TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000003', '51000000-0000-0000-0000-000000000001', 'PARTICIPANTES_E_PRESENCAS', 3, 'Participantes e presenças', 'Não omita ausências confirmadas.', TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000004', '51000000-0000-0000-0000-000000000001', 'PAUTA', 4, 'Pauta', NULL, TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000005', '51000000-0000-0000-0000-000000000001', 'DELIBERACOES', 5, 'Deliberações e encaminhamentos', 'Marque como pendente o que não estiver confirmado.', TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000006', '51000000-0000-0000-0000-000000000001', 'ASSINATURAS', 6, 'Assinaturas', 'Não declare assinatura sem a cópia assinada.', TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000011', '51000000-0000-0000-0000-000000000002', 'IDENTIFICACAO_REUNIAO', 1, 'Identificação do encontro', NULL, TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000012', '51000000-0000-0000-0000-000000000002', 'DATA_E_LOCAL', 2, 'Data e local', NULL, TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000013', '51000000-0000-0000-0000-000000000002', 'PARTICIPANTES_E_PRESENCAS', 3, 'Participantes', NULL, TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000014', '51000000-0000-0000-0000-000000000002', 'PAUTA', 4, 'Assuntos tratados', NULL, TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000015', '51000000-0000-0000-0000-000000000002', 'DELIBERACOES', 5, 'Registros e encaminhamentos', NULL, TRUE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE),
    ('52000000-0000-0000-0000-000000000016', '51000000-0000-0000-0000-000000000002', 'ASSINATURAS', 6, 'Assinaturas quando aplicáveis', NULL, FALSE, 0, CURRENT_TIMESTAMP, 'MIGRATION_V95', TRUE);
