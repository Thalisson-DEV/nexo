-- Intencao unica enviada pelo usuario. Os filhos operacionais continuam em
-- solicitacoes_material, um por tipo tecnico derivado.
CREATE TABLE agrupamentos_solicitacao_tecnica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    versao BIGINT NOT NULL DEFAULT 0,
    id_obra UUID NOT NULL,
    numero_obra_snapshot VARCHAR(30) NOT NULL,
    titulo_obra_snapshot VARCHAR(300) NOT NULL,
    tipo_obra_snapshot VARCHAR(20) NOT NULL,
    ocorrencia VARCHAR(100),
    id_almoxarifado_atendimento UUID NOT NULL REFERENCES estoques(id),
    id_equipe_saque UUID,
    data_prevista_retirada DATE,
    observacao VARCHAR(1000),
    origem VARCHAR(20) NOT NULL DEFAULT 'UI',
    id_usuario_criacao UUID NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_agrupamentos_tipo_obra
        CHECK (tipo_obra_snapshot IN ('OBRA', 'CORRETIVA', 'PREVENTIVA', 'LPT')),
    CONSTRAINT ck_agrupamentos_origem
        CHECK (origem IN ('UI', 'IMPORT', 'SYSTEM'))
);

CREATE INDEX idx_agrupamentos_solicitacao_obra
    ON agrupamentos_solicitacao_tecnica(id_obra);
CREATE INDEX idx_agrupamentos_solicitacao_almoxarifado
    ON agrupamentos_solicitacao_tecnica(id_almoxarifado_atendimento);
CREATE INDEX idx_agrupamentos_solicitacao_equipe
    ON agrupamentos_solicitacao_tecnica(id_equipe_saque);

ALTER TABLE solicitacoes_material
    ADD CONSTRAINT fk_solicitacoes_material_agrupamento
        FOREIGN KEY (id_agrupador)
        REFERENCES agrupamentos_solicitacao_tecnica(id)
        ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_solicitacoes_material_agrupamento_tipo
    ON solicitacoes_material(id_agrupador, tipo_tecnico)
    WHERE id_agrupador IS NOT NULL AND tipo_tecnico IS NOT NULL;

CREATE INDEX idx_solicitacoes_material_agrupamento
    ON solicitacoes_material(id_agrupador);

-- Reserva externa: um registro por filho, numero propositalmente nao global.
CREATE TABLE reservas_solicitacao_tecnica (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_solicitacao UUID NOT NULL
        REFERENCES solicitacoes_material(id) ON DELETE RESTRICT,
    numero NUMERIC(20, 0) NOT NULL,
    data_criacao DATE NOT NULL,
    id_agente_responsavel UUID NOT NULL,
    nome_agente_snapshot VARCHAR(200) NOT NULL,
    empresa_agente_snapshot VARCHAR(200),
    registrada_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_usuario_registro UUID NOT NULL,
    atualizada_em TIMESTAMP WITH TIME ZONE,
    id_usuario_atualizacao UUID,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_reservas_solicitacao_filho UNIQUE (id_solicitacao),
    CONSTRAINT ck_reservas_numero_nao_negativo CHECK (numero >= 0)
);

CREATE INDEX idx_reservas_solicitacao_numero
    ON reservas_solicitacao_tecnica(numero);
CREATE INDEX idx_reservas_solicitacao_agente
    ON reservas_solicitacao_tecnica(id_agente_responsavel);

-- Fatos operacionais imutaveis do filho.
CREATE TABLE operacoes_solicitacao_material (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_agrupador UUID NOT NULL
        REFERENCES agrupamentos_solicitacao_tecnica(id) ON DELETE RESTRICT,
    id_solicitacao UUID NOT NULL
        REFERENCES solicitacoes_material(id) ON DELETE RESTRICT,
    tipo VARCHAR(20) NOT NULL,
    tipo_tecnico VARCHAR(3) NOT NULL,
    tipo_obra_snapshot VARCHAR(20) NOT NULL,
    ocorrencia VARCHAR(100),
    id_reserva UUID
        REFERENCES reservas_solicitacao_tecnica(id) ON DELETE RESTRICT,
    id_operacao_original UUID
        REFERENCES operacoes_solicitacao_material(id) ON DELETE RESTRICT,
    id_obra UUID NOT NULL,
    id_equipe UUID,
    id_usuario_operador UUID NOT NULL,
    realizada_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo VARCHAR(500),
    assinatura_almox TEXT,
    assinatura_equipe TEXT,
    origem VARCHAR(20) NOT NULL DEFAULT 'UI',
    CONSTRAINT ck_operacoes_solicitacao_tipo
        CHECK (tipo IN ('ENTREGA', 'DEVOLUCAO', 'ESTORNO', 'ENCERRAMENTO')),
    CONSTRAINT ck_operacoes_solicitacao_tipo_tecnico
        CHECK (tipo_tecnico IN ('221', '921', '222', '922')),
    CONSTRAINT ck_operacoes_solicitacao_tipo_obra
        CHECK (tipo_obra_snapshot IN ('OBRA', 'CORRETIVA', 'PREVENTIVA', 'LPT')),
    CONSTRAINT ck_operacoes_solicitacao_origem
        CHECK (origem IN ('UI', 'IMPORT', 'SYSTEM')),
    CONSTRAINT ck_operacoes_solicitacao_referencia_estorno
        CHECK (
            (tipo = 'ESTORNO' AND id_operacao_original IS NOT NULL AND motivo IS NOT NULL)
            OR (tipo <> 'ESTORNO' AND id_operacao_original IS NULL)
        ),
    CONSTRAINT ck_operacoes_solicitacao_motivo_encerramento
        CHECK (tipo <> 'ENCERRAMENTO' OR motivo IS NOT NULL)
);

CREATE INDEX idx_operacoes_solicitacao_agrupamento
    ON operacoes_solicitacao_material(id_agrupador);
CREATE INDEX idx_operacoes_solicitacao_filho
    ON operacoes_solicitacao_material(id_solicitacao);
CREATE INDEX idx_operacoes_solicitacao_original
    ON operacoes_solicitacao_material(id_operacao_original);
CREATE INDEX idx_operacoes_solicitacao_obra
    ON operacoes_solicitacao_material(id_obra);

CREATE TABLE itens_operacao_solicitacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_operacao UUID NOT NULL
        REFERENCES operacoes_solicitacao_material(id) ON DELETE RESTRICT,
    id_item_solicitacao UUID NOT NULL
        REFERENCES itens_solicitacao(id) ON DELETE RESTRICT,
    id_material UUID NOT NULL
        REFERENCES materiais(id) ON DELETE RESTRICT,
    codigo_material_snapshot VARCHAR(100) NOT NULL,
    descricao_material_snapshot VARCHAR(355) NOT NULL,
    classificacao_tecnica_snapshot VARCHAR(20) NOT NULL,
    quantidade NUMERIC(12, 3) NOT NULL,
    num_serie VARCHAR(255),
    id_item_operacao_original UUID
        REFERENCES itens_operacao_solicitacao(id) ON DELETE RESTRICT,
    CONSTRAINT ck_itens_operacao_classificacao
        CHECK (classificacao_tecnica_snapshot IN ('COMUM', 'CONCRETO')),
    CONSTRAINT ck_itens_operacao_quantidade_positiva
        CHECK (quantidade > 0)
);

CREATE INDEX idx_itens_operacao_operacao
    ON itens_operacao_solicitacao(id_operacao);
CREATE INDEX idx_itens_operacao_item_solicitacao
    ON itens_operacao_solicitacao(id_item_solicitacao);
CREATE INDEX idx_itens_operacao_material
    ON itens_operacao_solicitacao(id_material);

-- Registro persistente de repeticao segura de comandos criticos.
CREATE TABLE comandos_idempotentes_solicitacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escopo VARCHAR(100) NOT NULL,
    id_usuario UUID NOT NULL,
    chave VARCHAR(200) NOT NULL,
    hash_payload CHAR(64) NOT NULL,
    id_agrupador UUID
        REFERENCES agrupamentos_solicitacao_tecnica(id) ON DELETE RESTRICT,
    id_solicitacao UUID
        REFERENCES solicitacoes_material(id) ON DELETE RESTRICT,
    id_operacao UUID
        REFERENCES operacoes_solicitacao_material(id) ON DELETE RESTRICT,
    resposta JSONB,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_comandos_idempotentes_escopo_usuario_chave
        UNIQUE (escopo, id_usuario, chave),
    CONSTRAINT ck_comandos_idempotentes_hash
        CHECK (hash_payload ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_comandos_idempotentes_solicitacao
    ON comandos_idempotentes_solicitacao(id_solicitacao);
CREATE INDEX idx_comandos_idempotentes_operacao
    ON comandos_idempotentes_solicitacao(id_operacao);

-- Eventos nao herdam BaseEntity: nao possuem colunas mutaveis ou soft delete.
CREATE TABLE eventos_solicitacao_material (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escopo VARCHAR(20) NOT NULL,
    id_agrupador UUID
        REFERENCES agrupamentos_solicitacao_tecnica(id) ON DELETE RESTRICT,
    id_solicitacao UUID
        REFERENCES solicitacoes_material(id) ON DELETE RESTRICT,
    sequencia BIGINT NOT NULL,
    tipo_evento VARCHAR(80) NOT NULL,
    tipo_tecnico VARCHAR(3),
    tipo_obra_snapshot VARCHAR(20) NOT NULL,
    ocorrencia VARCHAR(100),
    id_autor UUID,
    autor_snapshot VARCHAR(200) NOT NULL,
    origem VARCHAR(20) NOT NULL,
    justificativa VARCHAR(1000),
    versao_agregado BIGINT NOT NULL,
    payload_canonico JSONB NOT NULL,
    hash_anterior CHAR(64),
    hash_atual CHAR(64),
    versao_chave VARCHAR(50),
    ocorrido_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_eventos_solicitacao_escopo
        CHECK (escopo IN ('AGRUPADOR', 'SOLICITACAO')),
    CONSTRAINT ck_eventos_solicitacao_cadeia
        CHECK (
            (escopo = 'AGRUPADOR' AND id_agrupador IS NOT NULL AND id_solicitacao IS NULL)
            OR (escopo = 'SOLICITACAO' AND id_solicitacao IS NOT NULL)
        ),
    CONSTRAINT ck_eventos_solicitacao_tipo_tecnico
        CHECK (tipo_tecnico IS NULL OR tipo_tecnico IN ('221', '921', '222', '922')),
    CONSTRAINT ck_eventos_solicitacao_tipo_obra
        CHECK (tipo_obra_snapshot IN ('OBRA', 'CORRETIVA', 'PREVENTIVA', 'LPT')),
    CONSTRAINT ck_eventos_solicitacao_origem
        CHECK (origem IN ('UI', 'IMPORT', 'SYSTEM')),
    CONSTRAINT ck_eventos_solicitacao_sequencia
        CHECK (sequencia > 0),
    CONSTRAINT ck_eventos_solicitacao_hash_anterior
        CHECK (hash_anterior IS NULL OR hash_anterior ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_eventos_solicitacao_hash_atual
        CHECK (hash_atual IS NULL OR hash_atual ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX uq_eventos_solicitacao_cadeia_agrupador
    ON eventos_solicitacao_material(id_agrupador, sequencia)
    WHERE escopo = 'AGRUPADOR';
CREATE UNIQUE INDEX uq_eventos_solicitacao_cadeia_filho
    ON eventos_solicitacao_material(id_solicitacao, sequencia)
    WHERE escopo = 'SOLICITACAO';
CREATE INDEX idx_eventos_solicitacao_ocorrido_em
    ON eventos_solicitacao_material(ocorrido_em DESC, id DESC);
CREATE INDEX idx_eventos_solicitacao_tipo
    ON eventos_solicitacao_material(tipo_evento);

-- A previa validada fica no servidor; o arquivo bruto nunca e armazenado.
CREATE TABLE lotes_importacao_solicitacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash CHAR(64) NOT NULL,
    hash_arquivo CHAR(64) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    formato VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    previa JSONB NOT NULL,
    total_linhas INTEGER NOT NULL,
    total_agrupamentos INTEGER NOT NULL,
    criado_por UUID NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmado_por UUID,
    confirmado_em TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_lotes_importacao_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_lotes_importacao_token_hash
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_lotes_importacao_hash_arquivo
        CHECK (hash_arquivo ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_lotes_importacao_formato
        CHECK (formato IN ('XLSX', 'CSV')),
    CONSTRAINT ck_lotes_importacao_status
        CHECK (status IN ('VALIDADO', 'PARCIALMENTE_CONFIRMADO', 'CONFIRMADO', 'EXPIRADO')),
    CONSTRAINT ck_lotes_importacao_totais
        CHECK (total_linhas >= 0 AND total_agrupamentos >= 0),
    CONSTRAINT ck_lotes_importacao_expiracao
        CHECK (expira_em > criado_em)
);

CREATE INDEX idx_lotes_importacao_criado_por
    ON lotes_importacao_solicitacao(criado_por);
CREATE INDEX idx_lotes_importacao_expira_em
    ON lotes_importacao_solicitacao(expira_em);
