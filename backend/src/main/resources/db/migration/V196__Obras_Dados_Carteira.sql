-- =====================================================================
-- V196: Dados de carteira da obra, histórico de status e deslocamento da base
--
-- Todas as colunas novas são opcionais: obras existentes continuam válidas e o
-- cadastro atual não muda. O "prazo" da carteira é a data prevista de conclusão
-- já existente; aqui entra só o prazo regulado.
--
-- O deslocamento guarda a base e as coordenadas usadas no cálculo, para saber
-- quando ele ficou desatualizado (base ou coordenadas da obra mudaram).
-- =====================================================================

ALTER TABLE obras
    ADD COLUMN id_contrato UUID,
    ADD COLUMN classe_tensao VARCHAR(20),
    ADD COLUMN classe_tensao_informada_por VARCHAR(255),
    ADD COLUMN classe_tensao_informada_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN prazo_regulado DATE,
    ADD COLUMN prioridade VARCHAR(20),
    ADD COLUMN lote_previsto VARCHAR(40),
    ADD COLUMN situacao_carteira VARCHAR(60),
    ADD COLUMN valor_material_previsto NUMERIC(15, 2),
    ADD COLUMN valor_servico_previsto NUMERIC(15, 2),
    ADD COLUMN observacao_status VARCHAR(1000),
    ADD COLUMN distancia_base_km NUMERIC(10, 2),
    ADD COLUMN tempo_deslocamento_min INTEGER,
    ADD COLUMN deslocamento_estimado BOOLEAN,
    ADD COLUMN deslocamento_calculado_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN id_base_deslocamento UUID,
    ADD COLUMN latitude_deslocamento DOUBLE PRECISION,
    ADD COLUMN longitude_deslocamento DOUBLE PRECISION;

ALTER TABLE obras
    ADD CONSTRAINT ck_obras_classe_tensao
        CHECK (classe_tensao IS NULL OR classe_tensao IN ('BT', 'MT_15', 'MT_25', 'MT_36', 'AT_72', 'AT_138')),
    ADD CONSTRAINT ck_obras_prioridade
        CHECK (prioridade IS NULL OR prioridade IN ('BAIXA', 'NORMAL', 'ALTA', 'URGENTE'));

CREATE INDEX idx_obras_contrato ON obras (id_contrato);
CREATE INDEX idx_obras_prioridade ON obras (prioridade);
CREATE INDEX idx_obras_coordenadas ON obras (latitude, longitude) WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

CREATE TABLE historico_status_obras (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_status_anterior UUID REFERENCES status_obras (id),
    id_status_novo UUID NOT NULL REFERENCES status_obras (id),
    observacao VARCHAR(1000),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_historico_status_obras_obra ON historico_status_obras (id_obra, data_criacao DESC);
