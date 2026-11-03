-- =====================================================================
-- V208: Medições das obras e pré-fechamento do levantamento
--
-- O levantamento é concluído no planejamento da obra, só sem ponto ou vão
-- sem kit; quem concluiu e quando ficam gravados.
--
-- A medição leva serviços concluídos à cobrança. O item guarda cópia de
-- código, descrição, local e valores, que vêm dos apontamentos com o valor
-- congelado na programação. Um item só pode estar em uma medição ativa: o
-- estorno desativa os itens e mantém a medição no histórico.
--
-- A competência é o primeiro dia do mês de referência.
-- =====================================================================

ALTER TABLE planejamentos_obras
    ADD COLUMN levantamento_concluido_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN levantamento_concluido_por VARCHAR(255);

CREATE SEQUENCE medicoes_obras_numero_seq;

CREATE TABLE medicoes_obras (
    id UUID PRIMARY KEY,
    numero BIGINT NOT NULL UNIQUE,
    id_obra UUID NOT NULL REFERENCES obras (id),
    tipo VARCHAR(20) NOT NULL,
    competencia DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'GERADA',
    valor_total NUMERIC(15, 2) NOT NULL,
    observacao VARCHAR(1000),
    justificativa_estorno VARCHAR(1000),
    data_estorno TIMESTAMP WITH TIME ZONE,
    usuario_estorno VARCHAR(255),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_medicoes_obras_tipo CHECK (tipo IN ('PARCIAL', 'CICLO')),
    CONSTRAINT ck_medicoes_obras_status CHECK (status IN ('GERADA', 'ESTORNADA')),
    CONSTRAINT ck_medicoes_obras_competencia CHECK (EXTRACT(DAY FROM competencia) = 1),
    CONSTRAINT ck_medicoes_obras_estorno CHECK (status <> 'ESTORNADA' OR justificativa_estorno IS NOT NULL)
);

CREATE INDEX idx_medicoes_obras_obra ON medicoes_obras (id_obra, competencia);
CREATE INDEX idx_medicoes_obras_competencia ON medicoes_obras (competencia) WHERE status = 'GERADA';

CREATE TABLE medicao_itens (
    id UUID PRIMARY KEY,
    id_medicao UUID NOT NULL REFERENCES medicoes_obras (id),
    id_item_planejado UUID NOT NULL REFERENCES itens_planejados (id),
    id_kit_aplicado UUID NOT NULL REFERENCES kits_aplicados (id),
    id_ponto UUID REFERENCES pontos_planejamento (id),
    id_vao UUID REFERENCES vaos_planejamento (id),
    codigo_kit VARCHAR(60),
    codigo VARCHAR(60),
    descricao VARCHAR(300),
    operacao VARCHAR(20) NOT NULL,
    unidade VARCHAR(20),
    quantidade NUMERIC(14, 4) NOT NULL,
    valor_unitario NUMERIC(15, 4),
    valor_total NUMERIC(15, 2),
    concluido_em DATE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_medicao_itens_medicao ON medicao_itens (id_medicao);
CREATE UNIQUE INDEX uq_medicao_itens_item_ativo ON medicao_itens (id_item_planejado) WHERE ativo = TRUE;
