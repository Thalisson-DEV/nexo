-- =====================================================================
-- V202: Programação das obras
--
-- A programação liga itens planejados a uma equipe e a um período. O valor de
-- cada serviço fica congelado em programacao_itens pelo valor vigente na data
-- de início: reajuste posterior da Ação não muda o que já foi programado.
--
-- Um item só pode estar em uma programação por vez. Item retirado ou de
-- programação cancelada fica desativado aqui, como histórico, e pode voltar a
-- ser programado.
--
-- O evento guarda números e códigos como texto: o histórico continua legível
-- e pesquisável mesmo depois de a programação, a obra ou a equipe mudarem.
--
-- Jornada e tempo de plataforma vêm do tipo de equipe, com ajuste opcional na
-- equipe. Capacidade e meta são calculadas na leitura e não ficam gravadas.
-- =====================================================================

ALTER TABLE tipos_equipe
    ADD COLUMN jornada_minutos INTEGER NOT NULL DEFAULT 480,
    ADD COLUMN plataforma_minutos INTEGER NOT NULL DEFAULT 30;

ALTER TABLE equipes
    ADD COLUMN jornada_minutos INTEGER,
    ADD COLUMN plataforma_minutos INTEGER;

CREATE SEQUENCE programacoes_numero_seq;

CREATE TABLE programacoes (
    id UUID PRIMARY KEY,
    numero BIGINT NOT NULL UNIQUE,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_equipe UUID NOT NULL REFERENCES equipes (id),
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    descricao VARCHAR(500) NOT NULL,
    linha_viva BOOLEAN NOT NULL,
    desligamento BOOLEAN NOT NULL,
    finaliza_obra BOOLEAN NOT NULL,
    turno VARCHAR(20) NOT NULL,
    situacao_intervencao VARCHAR(20) NOT NULL,
    observacao VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADA',
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_programacoes_periodo CHECK (data_fim >= data_inicio),
    CONSTRAINT ck_programacoes_turno CHECK (turno IN ('DIA_TODO', 'MANHA', 'TARDE')),
    CONSTRAINT ck_programacoes_situacao CHECK (situacao_intervencao IN ('CRIADO', 'PENDENTE', 'NAO_SE_APLICA')),
    CONSTRAINT ck_programacoes_status CHECK (status IN ('PROGRAMADA', 'CANCELADA'))
);

CREATE INDEX idx_programacoes_obra ON programacoes (id_obra) WHERE ativo = TRUE;
CREATE INDEX idx_programacoes_equipe_periodo ON programacoes (id_equipe, data_inicio, data_fim) WHERE ativo = TRUE;

CREATE TABLE programacao_itens (
    id UUID PRIMARY KEY,
    id_programacao UUID NOT NULL REFERENCES programacoes (id),
    id_item_planejado UUID NOT NULL REFERENCES itens_planejados (id),
    quantidade NUMERIC(14, 4) NOT NULL,
    valor_unitario NUMERIC(15, 4),
    valor_total NUMERIC(15, 2),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_programacao_itens_programacao ON programacao_itens (id_programacao);
CREATE UNIQUE INDEX uq_programacao_itens_item_ativo ON programacao_itens (id_item_planejado) WHERE ativo = TRUE;

CREATE TABLE eventos_programacao (
    id UUID PRIMARY KEY,
    id_programacao UUID NOT NULL REFERENCES programacoes (id),
    numero_programacao BIGINT NOT NULL,
    id_obra UUID NOT NULL REFERENCES obras (id),
    numero_obra VARCHAR(255),
    tipo VARCHAR(20) NOT NULL,
    id_equipe_origem UUID,
    equipe_origem VARCHAR(50),
    id_equipe_destino UUID,
    equipe_destino VARCHAR(50),
    inicio_original DATE,
    fim_original DATE,
    inicio_novo DATE,
    fim_novo DATE,
    observacao VARCHAR(2000),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_eventos_programacao_tipo CHECK (tipo IN ('EDITADA', 'REPROGRAMADA', 'CANCELADA', 'EXCLUIDA'))
);

CREATE INDEX idx_eventos_programacao_data ON eventos_programacao (data_criacao DESC);
CREATE INDEX idx_eventos_programacao_programacao ON eventos_programacao (id_programacao);
