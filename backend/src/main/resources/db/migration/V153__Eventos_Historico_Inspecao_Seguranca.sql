-- =============================================================================================
-- V153: Histórico e auditoria de inspeções de segurança
-- Registra eventos encadeados e auditáveis (criação, edição de cabeçalho e tratamento de desvios)
-- no mesmo padrão adotado pelas solicitações técnicas do processo técnico.
-- =============================================================================================

CREATE TABLE IF NOT EXISTS eventos_inspecao_seguranca (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_inspecao UUID NOT NULL
        REFERENCES inspecoes_seguranca(id) ON DELETE CASCADE,
    sequencia BIGINT NOT NULL,
    tipo_evento VARCHAR(80) NOT NULL,
    id_autor UUID,
    autor_snapshot VARCHAR(200) NOT NULL,
    origem VARCHAR(20) NOT NULL DEFAULT 'UI',
    justificativa VARCHAR(1000),
    payload_canonico TEXT NOT NULL,
    hash_anterior CHAR(64),
    hash_atual CHAR(64),
    ocorrido_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_eventos_inspecao_sequencia CHECK (sequencia > 0),
    CONSTRAINT ck_eventos_inspecao_origem CHECK (origem IN ('UI', 'IMPORT', 'SYSTEM')),
    CONSTRAINT uq_eventos_inspecao_sequencia UNIQUE (id_inspecao, sequencia)
);

CREATE INDEX IF NOT EXISTS idx_eventos_inspecao_ocorrido_em
    ON eventos_inspecao_seguranca(id_inspecao, ocorrido_em DESC);

CREATE INDEX IF NOT EXISTS idx_eventos_inspecao_tipo
    ON eventos_inspecao_seguranca(tipo_evento);
