-- 1. Adicionar vínculo com usuário na tabela de equipes
ALTER TABLE equipes ADD COLUMN id_usuario UUID UNIQUE;

-- 2. Criar tabela de histórico de membros (EquipeMembro)
CREATE TABLE equipe_membros (
    id UUID PRIMARY KEY,
    id_equipe UUID NOT NULL,
    id_colaborador UUID NOT NULL,
    funcao VARCHAR(20) NOT NULL, -- RESPONSAVEL, PARCEIRO
    data_entrada TIMESTAMP WITH TIME ZONE NOT NULL,
    data_saida TIMESTAMP WITH TIME ZONE,
    
    -- Campos BaseEntity
    versao BIGINT,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_equipe_membros_equipe FOREIGN KEY (id_equipe) REFERENCES equipes(id),
    CONSTRAINT fk_equipe_membros_colaborador FOREIGN KEY (id_colaborador) REFERENCES colaboradores(id)
);

-- 3. Índices para performance em buscas históricas
CREATE INDEX idx_equipe_membros_equipe ON equipe_membros(id_equipe);
CREATE INDEX idx_equipe_membros_colaborador ON equipe_membros(id_colaborador);
CREATE INDEX idx_equipe_membros_ativo ON equipe_membros(id_equipe, id_colaborador) WHERE data_saida IS NULL;
