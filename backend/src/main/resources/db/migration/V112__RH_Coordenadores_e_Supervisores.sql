-- =====================================================================
-- V112: RH - Coordenadores e Supervisores
--
-- Domínio próprio para gestão de coordenadores e supervisores no módulo de RH.
-- Um supervisor sempre responde a um coordenador cadastrado.
-- Ambos possuem período de vigência (data_inicio obrigatória, data_fim opcional).
-- =====================================================================

-- 1. Tabela de Coordenadores
CREATE TABLE coordenadores (
    id UUID PRIMARY KEY,
    id_colaborador UUID NOT NULL REFERENCES colaboradores(id),
    data_inicio DATE NOT NULL,
    data_fim DATE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_coordenadores_colaborador ON coordenadores(id_colaborador);
CREATE INDEX idx_coordenadores_ativo ON coordenadores(ativo);
CREATE INDEX idx_coordenadores_datas ON coordenadores(data_inicio, data_fim);

-- 2. Tabela de Supervisores
CREATE TABLE supervisores (
    id UUID PRIMARY KEY,
    id_colaborador UUID NOT NULL REFERENCES colaboradores(id),
    id_coordenador UUID NOT NULL REFERENCES coordenadores(id),
    data_inicio DATE NOT NULL,
    data_fim DATE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_supervisores_colaborador ON supervisores(id_colaborador);
CREATE INDEX idx_supervisores_coordenador ON supervisores(id_coordenador);
CREATE INDEX idx_supervisores_ativo ON supervisores(ativo);
CREATE INDEX idx_supervisores_datas ON supervisores(data_inicio, data_fim);

-- 3. Permissões IAM
INSERT INTO permissoes (id, versao, data_criacao, ativo, nome) VALUES
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:coordenador:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:coordenador:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:supervisor:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:supervisor:write');

-- 4. Vincular novas permissões ao perfil ADMINISTRADOR
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pa.id, p.id
FROM perfis_acesso pa, permissoes p
WHERE pa.nome = 'ADMINISTRADOR'
  AND p.nome IN ('rh:coordenador:read', 'rh:coordenador:write', 'rh:supervisor:read', 'rh:supervisor:write');
