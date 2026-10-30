-- =====================================================================
-- V142: Tipos de Inspeção de Segurança no módulo de SST
--
-- Introduz a parametrização de modalidades de inspeção (Alojamento,
-- Campo, Câmera, Veicular) e vincula obrigatoriamente as inspeções de
-- segurança e metas de inspeção ao seu respectivo tipo.
-- =====================================================================

CREATE TABLE tipos_inspecao (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    nome_normalizado VARCHAR(120) NOT NULL,
    descricao VARCHAR(500),
    ordem INTEGER NOT NULL DEFAULT 0,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX ux_tipo_inspecao_nome_ativa
    ON tipos_inspecao (nome_normalizado) WHERE ativo;

CREATE INDEX ix_tipo_inspecao_ordem ON tipos_inspecao (ordem);

-- Carga inicial dos 4 tipos padrão
INSERT INTO tipos_inspecao (
    id, nome, nome_normalizado, descricao, ordem,
    versao, data_criacao, usuario_criacao, ativo
) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'Alojamento', 'alojamento', 'Inspeções realizadas em alojamentos e instalações habitacionais.', 1, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('c1000000-0000-0000-0000-000000000002', 'Campo', 'campo', 'Inspeções de segurança realizadas em campo e frentes de serviço.', 2, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('c2000000-0000-0000-0000-000000000003', 'Câmera', 'camera', 'Inspeções e auditorias realizadas via monitoramento de câmeras.', 3, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('d1000000-0000-0000-0000-000000000004', 'Veicular', 'veicular', 'Inspeções veiculares e de equipamentos de transporte.', 4, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

-- Vínculo em inspeções de segurança
ALTER TABLE inspecoes_seguranca
    ADD COLUMN id_tipo_inspecao UUID;

UPDATE inspecoes_seguranca
SET id_tipo_inspecao = 'c1000000-0000-0000-0000-000000000002'
WHERE id_tipo_inspecao IS NULL;

ALTER TABLE inspecoes_seguranca
    ALTER COLUMN id_tipo_inspecao SET NOT NULL;

ALTER TABLE inspecoes_seguranca
    ADD CONSTRAINT fk_inspecoes_tipo_inspecao
        FOREIGN KEY (id_tipo_inspecao) REFERENCES tipos_inspecao (id);

CREATE INDEX ix_inspecoes_tipo_inspecao ON inspecoes_seguranca (id_tipo_inspecao);

-- Vínculo em metas de inspeção
ALTER TABLE metas_inspecao
    ADD COLUMN id_tipo_inspecao UUID;

UPDATE metas_inspecao
SET id_tipo_inspecao = 'c1000000-0000-0000-0000-000000000002'
WHERE id_tipo_inspecao IS NULL;

ALTER TABLE metas_inspecao
    ALTER COLUMN id_tipo_inspecao SET NOT NULL;

ALTER TABLE metas_inspecao
    ADD CONSTRAINT fk_metas_tipo_inspecao
        FOREIGN KEY (id_tipo_inspecao) REFERENCES tipos_inspecao (id);

CREATE INDEX ix_metas_tipo_inspecao ON metas_inspecao (id_tipo_inspecao);
