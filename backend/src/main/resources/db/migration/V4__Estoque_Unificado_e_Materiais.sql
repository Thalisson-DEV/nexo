CREATE TABLE tipos_estoque (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(20) NOT NULL UNIQUE, -- FISICO, CAMPO
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE estoques (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome_identificador VARCHAR(100) NOT NULL,
    id_tipo_estoque UUID REFERENCES tipos_estoque(id),
    id_equipe UUID REFERENCES equipes(id), -- Apenas para estoque de CAMPO
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE avaliacoes_material (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(50) NOT NULL UNIQUE, -- INVEST, MANUT, RECUP, SUCATA
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE centros_investimento (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    codigo VARCHAR(10) NOT NULL UNIQUE, -- 670, 610
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE materiais (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cod_material VARCHAR(50) NOT NULL UNIQUE,
    descricao TEXT NOT NULL,
    unidade_medida VARCHAR(10),
    supr_matr_flag VARCHAR(4), -- SUPR ou MATR
    id_avaliacao_tecnica UUID REFERENCES avaliacoes_material(id),
    id_centro_investimento UUID REFERENCES centros_investimento(id),
    detalha_serie BOOLEAN DEFAULT FALSE,
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE saldos (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_estoque UUID REFERENCES estoques(id),
    id_material UUID REFERENCES materiais(id),
    quantidade DECIMAL(12,3) DEFAULT 0,
    CONSTRAINT unq_estoque_material UNIQUE(id_estoque, id_material)
);