-- =====================================================================
-- V191: Alteração de nota já sincronizada
--
-- O campo (no app, inclusive offline) e o escritório passam a poder
-- corrigir os serviços e os materiais de uma nota depois que ela chegou
-- ao servidor. A nota continua CONCLUIDO (faturamento e metas seguem
-- contando), mas fica marcada como alterada e cada mudança vira um
-- evento imutável da linha do tempo.
--
-- * total_alteracoes: quantas vezes a nota foi alterada (0 = como foi
--   lançada). As colunas id_usuario_alteracao e data_alteracao, que já
--   existiam sem uso, guardam a última alteração.
-- * execucoes_historico: LANCADA, ALTERADA e NUMERO_OFICIAL, com quem,
--   de onde (CAMPO ou ESCRITORIO), o motivo e o que entrou e saiu, com
--   os nomes congelados no momento do evento.
-- * id_operacao: gerado pelo aparelho para a alteração feita offline;
--   o reenvio da mesma alteração não a aplica duas vezes.
-- =====================================================================

ALTER TABLE execucoes_comercial
    ADD COLUMN total_alteracoes INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_execucoes_comercial_total_alteracoes CHECK (total_alteracoes >= 0);

CREATE INDEX ix_execucoes_comercial_alteradas
    ON execucoes_comercial (data_alteracao)
    WHERE total_alteracoes > 0 AND ativo;

CREATE TABLE execucoes_historico
(
    id             UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    id_execucao    UUID                     NOT NULL REFERENCES execucoes_comercial (id),
    tipo           VARCHAR(30)              NOT NULL,
    origem         VARCHAR(20)              NOT NULL,
    id_autor       UUID,
    autor_snapshot VARCHAR(200)             NOT NULL,
    motivo         VARCHAR(500),
    valor_anterior NUMERIC(12, 2),
    valor_novo     NUMERIC(12, 2),
    detalhes       JSONB                    NOT NULL DEFAULT '{}'::jsonb,
    id_operacao    UUID,
    ocorrido_em    TIMESTAMP WITH TIME ZONE NOT NULL,
    registrado_em  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_execucoes_historico_tipo CHECK (tipo IN ('LANCADA', 'ALTERADA', 'NUMERO_OFICIAL')),
    CONSTRAINT ck_execucoes_historico_origem CHECK (origem IN ('CAMPO', 'ESCRITORIO')),
    CONSTRAINT ck_execucoes_historico_motivo CHECK (tipo <> 'ALTERADA' OR length(trim(motivo)) > 0)
);

CREATE INDEX ix_execucoes_historico_execucao ON execucoes_historico (id_execucao, ocorrido_em);
CREATE UNIQUE INDEX ux_execucoes_historico_operacao ON execucoes_historico (id_operacao) WHERE id_operacao IS NOT NULL;
