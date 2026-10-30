-- V31: Separação de usuários de campo e gestão de turnos
ALTER TABLE usuarios ADD COLUMN tipo_usuario VARCHAR(20) DEFAULT 'ADMINISTRATIVO' NOT NULL;

CREATE TABLE turnos (
    id UUID PRIMARY KEY,
    id_equipe UUID NOT NULL REFERENCES equipes(id),
    id_usuario_abertura UUID NOT NULL REFERENCES usuarios(id),
    data_inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    data_fim TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL, -- ABERTO, FECHADO
    
    -- Campos de auditoria BaseEntity
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_turnos_equipe ON turnos(id_equipe);
CREATE INDEX idx_turnos_usuario_abertura ON turnos(id_usuario_abertura);
CREATE INDEX idx_turnos_status ON turnos(status);

ALTER TABLE execucoes_comercial ADD COLUMN id_turno UUID REFERENCES turnos(id);
CREATE INDEX idx_execucoes_comercial_turno ON execucoes_comercial(id_turno);
