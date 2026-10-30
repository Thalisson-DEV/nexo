-- V30: Tabela para persistência de eventos importantes de telemetria
CREATE TABLE telemetria_eventos (
    id UUID PRIMARY KEY,
    id_veiculo_vivo INTEGER NOT NULL,
    id_veiculo_ces UUID REFERENCES veiculos(id),
    tipo_operacao VARCHAR(20) NOT NULL, -- vEvent, alarmlog
    nome_evento VARCHAR(100) NOT NULL, -- ignition_on, Excesso de Velocidade, etc
    data_hora TIMESTAMP WITH TIME ZONE NOT NULL,
    latitude NUMERIC(10, 8),
    longitude NUMERIC(11, 8),
    payload_bruto JSONB,
    
    -- Campos de auditoria BaseEntity
    versao BIGINT,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_telemetria_eventos_veiculo ON telemetria_eventos(id_veiculo_ces);
CREATE INDEX idx_telemetria_eventos_data ON telemetria_eventos(data_hora DESC);
CREATE INDEX idx_telemetria_eventos_nome ON telemetria_eventos(nome_evento);

COMMENT ON TABLE telemetria_eventos IS 'Armazena eventos importantes recebidos da Vivo (ignição, alarmes e status)';
