CREATE TABLE contratos (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    numero_contrato VARCHAR(100) NOT NULL,
    cliente VARCHAR(100) DEFAULT 'Concessionária',
    data_vigencia DATE NOT NULL,
    data_encerramento DATE NOT NULL,
    id_base_operacional UUID REFERENCES bases_operacionais(id),
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE municipios (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(100) NOT NULL,
    id_base_operacional UUID REFERENCES bases_operacionais(id),
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE contrato_base_faturamento (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_contrato UUID REFERENCES contratos(id),
    id_base UUID REFERENCES bases_operacionais(id),
    coeficiente_faturamento DECIMAL(10,4) NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT unq_contrato_municipio UNIQUE(id_contrato, id_base)
);

CREATE TABLE acoes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cod_acao VARCHAR(20) NOT NULL UNIQUE,
    nome VARCHAR(255) NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE acoes_valores (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_acao UUID REFERENCES acoes(id),
    id_contrato UUID REFERENCES contratos(id),
    valor_base DECIMAL(12,2) NOT NULL,
    data_vigencia DATE NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT unq_acao_contrato_vigencia UNIQUE(id_acao, id_contrato, data_vigencia)
);