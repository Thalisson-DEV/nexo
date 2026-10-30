CREATE TABLE eventos_turno (
    id UUID PRIMARY KEY,
    id_turno UUID NOT NULL REFERENCES turnos(id),
    tipo_evento VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL, -- EM_ANDAMENTO, FINALIZADO
    data_inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    data_fim TIMESTAMP WITH TIME ZONE,
    observacao TEXT,
    lat_inicio DECIMAL(10, 8),
    lon_inicio DECIMAL(11, 8),
    lat_fim DECIMAL(10, 8),
    lon_fim DECIMAL(11, 8),

    -- Campos de auditoria BaseEntity
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_eventos_turno_id_turno ON eventos_turno(id_turno);
CREATE INDEX idx_eventos_turno_status ON eventos_turno(status);
CREATE INDEX idx_eventos_turno_tipo ON eventos_turno(tipo_evento);
