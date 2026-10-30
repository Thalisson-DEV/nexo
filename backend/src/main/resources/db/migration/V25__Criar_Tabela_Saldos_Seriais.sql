-- Criação da tabela de Saldos Seriais para rastreamento de itens individuais (Materializada)
CREATE TABLE saldos_seriais (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_estoque UUID NOT NULL REFERENCES estoques(id),
    id_material UUID NOT NULL REFERENCES materiais(id),
    num_serie VARCHAR(100) NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(100),
    usuario_ultima_alteracao VARCHAR(100),
    ativo BOOLEAN DEFAULT TRUE NOT NULL,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

-- Índices para performance de busca
CREATE INDEX idx_saldo_serie_estoque ON saldos_seriais(id_estoque);
CREATE INDEX idx_saldo_serie_material ON saldos_seriais(id_material);
CREATE INDEX idx_saldo_serie_num_serie ON saldos_seriais(num_serie);

-- Restrição: Um número de série ativo não pode estar em dois estoques ao mesmo tempo
CREATE UNIQUE INDEX unq_num_serie_ativo ON saldos_seriais(num_serie) WHERE ativo = TRUE;

COMMENT ON TABLE saldos_seriais IS 'Rastreia a localização atual de materiais que possuem controle por número de série.';
