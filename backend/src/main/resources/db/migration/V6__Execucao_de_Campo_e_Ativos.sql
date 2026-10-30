CREATE TABLE execucoes_comercial (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    referencia_nota VARCHAR(100), -- 🔒
    id_equipe UUID REFERENCES equipes(id),
    id_municipio UUID REFERENCES municipios(id),
    -- Ativos Concessionária
    num_poste VARCHAR(50), -- 🔒
    num_posto VARCHAR(50), -- 🔒
    num_serie_medidor VARCHAR(100), -- 🔒
    -- Metadados
    latitude_inicio DECIMAL(10,8),
    longitude_inicio DECIMAL(11,8),
    data_inicio TIMESTAMP WITH TIME ZONE NOT NULL,
    latitude_fim DECIMAL(10,8),
    longitude_fim DECIMAL(11,8),
    data_fim TIMESTAMP WITH TIME ZONE,
    status_execucao VARCHAR(20) DEFAULT 'CONCLUIDO', -- CONCLUIDO, EDITADO, ESTORNADO
    valor_total_faturado DECIMAL(12,2) DEFAULT 0,
    id_usuario_alteracao UUID REFERENCES usuarios(id),
    data_alteracao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE execucoes_materiais (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_execucao UUID REFERENCES execucoes_comercial(id) ON DELETE CASCADE,
    id_material UUID REFERENCES materiais(id),
    quantidade DECIMAL(12,3) NOT NULL,
    operacao VARCHAR(20) NOT NULL -- APLICADO, RETIRADO
);

CREATE TABLE execucoes_acoes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_execucao UUID REFERENCES execucoes_comercial(id) ON DELETE CASCADE,
    id_acao UUID REFERENCES acoes(id)
);

COMMENT ON COLUMN execucoes_comercial.referencia_nota IS '🔒 DADO SENSÍVEL';
COMMENT ON COLUMN execucoes_comercial.num_poste IS '🔒 DADO SENSÍVEL';
COMMENT ON COLUMN execucoes_comercial.num_posto IS '🔒 DADO SENSÍVEL';
COMMENT ON COLUMN execucoes_comercial.num_serie_medidor IS '🔒 DADO SENSÍVEL';