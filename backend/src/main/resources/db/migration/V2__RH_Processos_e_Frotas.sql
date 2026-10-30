CREATE TABLE processos_negocio (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(50) NOT NULL UNIQUE, -- COMERCIAL, LINHA VIVA, MANUTENÇÃO, LEITURA, CONSTRUÇÃO, ADM
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE tipos_equipe (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(50) NOT NULL UNIQUE, -- PESADA, LEVE, COMPRESSOR, RETRO, BLASTER
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE centros_faturamento (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(100) NOT NULL UNIQUE, -- CENTRO NORTE E NORTE
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE bases_operacionais (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(100) NOT NULL UNIQUE, -- BONFIM, JACOBINA, JUAZEIRO E REMANSO
    id_centro_custo UUID REFERENCES centros_faturamento(id),
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE veiculos (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    placa VARCHAR(10) NOT NULL UNIQUE,
    modelo VARCHAR(100) NOT NULL, -- Strada, Gol, Fiat Uno...
    fabricante VARCHAR(50),
    id_base_operacional UUID REFERENCES bases_operacionais(id),
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE funcoes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nome VARCHAR(100) NOT NULL UNIQUE,
    descricao VARCHAR(255),
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    versao BIGINT DEFAULT 0,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE TABLE colaboradores (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    id_usuario UUID UNIQUE REFERENCES usuarios(id),
    primeiro_nome VARCHAR(255) NOT NULL, -- 🔒 Criptografado
    ultimo_nome VARCHAR(255) NOT NULL, -- 🔒 Criptografado
    nome_completo VARCHAR(512) NOT NULL, -- 🔒 Criptografado
    nome_completo_hash VARCHAR(64) NOT NULL, -- 🔍 Blind Index
    cpf VARCHAR(255) NOT NULL UNIQUE, -- 🔒 Criptografado
    cpf_hash VARCHAR(64) NOT NULL UNIQUE, -- 🔍 Blind Index
    id_processo UUID REFERENCES processos_negocio(id),
    id_base_operacional UUID REFERENCES bases_operacionais(id),
    id_funcao UUID REFERENCES funcoes(id),
    data_contratacao DATE,
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_colaboradores_cpf_hash ON colaboradores(cpf_hash);
CREATE INDEX idx_colaboradores_nome_completo_hash ON colaboradores(nome_completo_hash);


CREATE TABLE equipes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cod_equipe VARCHAR(50) NOT NULL UNIQUE,
    id_tipo_equipe UUID REFERENCES tipos_equipe(id),
    id_veiculo_vinculado UUID REFERENCES veiculos(id),
    id_coordenador UUID REFERENCES colaboradores(id),
    id_supervisor UUID REFERENCES colaboradores(id),
    id_base_operacional UUID REFERENCES bases_operacionais(id),
    id_processo UUID REFERENCES processos_negocio(id),
    ativo BOOLEAN DEFAULT TRUE,
    data_criacao TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

COMMENT ON COLUMN colaboradores.cpf IS '🔒 DADO SENSÍVEL';