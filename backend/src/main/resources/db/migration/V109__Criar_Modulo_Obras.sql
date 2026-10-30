-- =====================================================================
-- V109: Criar Módulo de Obras
--
-- Domínio central para controle de obras, projetos e subprojetos (Medidor/Kit),
-- equipes vinculadas, anexos técnicos (As-Built, CAD, memoriais) e vínculos com
-- bases operacionais, processos, municípios e centros de custo.
-- =====================================================================

-- 1. Status de Obras
CREATE TABLE status_obras (
    id UUID PRIMARY KEY,
    nome VARCHAR(60) NOT NULL,
    ordem INTEGER NOT NULL DEFAULT 0,
    concluida BOOLEAN NOT NULL DEFAULT FALSE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_status_obras_nome UNIQUE (nome)
);

-- 2. Agentes Responsáveis da Concessionária/Cliente
CREATE TABLE agentes_responsaveis_obras (
    id UUID PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(150),
    telefone VARCHAR(30),
    empresa_orgao VARCHAR(100),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

-- 3. Tipos de Anexos de Obra
CREATE TABLE tipos_anexos_obras (
    id UUID PRIMARY KEY,
    nome VARCHAR(60) NOT NULL,
    descricao VARCHAR(255),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_tipos_anexos_obras_nome UNIQUE (nome)
);

-- 4. Obras (Entidade Principal)
CREATE TABLE obras (
    id UUID PRIMARY KEY,
    numero VARCHAR(50) NOT NULL,
    titulo VARCHAR(300) NOT NULL,
    descricao VARCHAR(1000),
    nota_obra VARCHAR(50),
    valor_projeto NUMERIC(15, 2),
    data_inicio DATE NOT NULL,
    data_previsao_fim DATE NOT NULL,
    data_fim_efetiva DATE,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    id_processo UUID,
    id_base_operacional UUID,
    id_municipio UUID,
    id_centro_custo UUID,
    id_status_obra UUID NOT NULL REFERENCES status_obras(id),
    id_agente_responsavel UUID REFERENCES agentes_responsaveis_obras(id),
    id_obra_pai UUID REFERENCES obras(id),
    tipo_vinculo_pai VARCHAR(50),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

-- 5. Equipes vinculadas à Obra (zero, uma ou muitas)
CREATE TABLE obras_equipes (
    id_obra UUID NOT NULL REFERENCES obras(id) ON DELETE CASCADE,
    id_equipe UUID NOT NULL,
    PRIMARY KEY (id_obra, id_equipe)
);

-- 6. Anexos da Obra
CREATE TABLE anexos_obras (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras(id) ON DELETE CASCADE,
    id_tipo_anexo UUID NOT NULL REFERENCES tipos_anexos_obras(id),
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    chave_storage VARCHAR(500) NOT NULL,
    observacoes VARCHAR(500),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

-- ═══════════════════════════════════════════════════════════════════════
-- Índices
-- ═══════════════════════════════════════════════════════════════════════

CREATE INDEX idx_obras_numero ON obras(numero);
CREATE INDEX idx_obras_nota_obra ON obras(nota_obra);
CREATE INDEX idx_obras_status ON obras(id_status_obra);
CREATE INDEX idx_obras_agente ON obras(id_agente_responsavel);
CREATE INDEX idx_obras_pai ON obras(id_obra_pai);
CREATE INDEX idx_obras_base ON obras(id_base_operacional);
CREATE INDEX idx_obras_processo ON obras(id_processo);
CREATE INDEX idx_obras_municipio ON obras(id_municipio);
CREATE INDEX idx_obras_centro_custo ON obras(id_centro_custo);
CREATE INDEX idx_obras_data_inicio ON obras(data_inicio);
CREATE INDEX idx_obras_data_previsao_fim ON obras(data_previsao_fim);
CREATE INDEX idx_obras_ativo ON obras(ativo);

CREATE INDEX idx_obras_equipes_equipe ON obras_equipes(id_equipe);
CREATE INDEX idx_anexos_obras_obra ON anexos_obras(id_obra);
CREATE INDEX idx_anexos_obras_tipo ON anexos_obras(id_tipo_anexo);

-- Índices GIN Trigram para busca rápida (buscaGlobal)
CREATE INDEX idx_obras_titulo_trgm ON obras USING gin (immutable_unaccent(lower(titulo)) gin_trgm_ops);
CREATE INDEX idx_obras_numero_trgm ON obras USING gin (immutable_unaccent(lower(numero)) gin_trgm_ops);
CREATE INDEX idx_obras_nota_trgm ON obras USING gin (immutable_unaccent(lower(nota_obra)) gin_trgm_ops);

-- ═══════════════════════════════════════════════════════════════════════
-- Seeds Iniciais
-- ═══════════════════════════════════════════════════════════════════════

INSERT INTO status_obras (id, nome, ordem, concluida, versao, data_criacao, usuario_criacao, ativo)
VALUES
    (gen_random_uuid(), 'PLANEJADA', 0, FALSE, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'EM EXECUÇÃO', 1, FALSE, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'PARALISADA', 2, FALSE, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'CONCLUÍDA', 3, TRUE, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'CANCELADA', 4, TRUE, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (nome) DO NOTHING;

INSERT INTO tipos_anexos_obras (id, nome, descricao, versao, data_criacao, usuario_criacao, ativo)
VALUES
    (gen_random_uuid(), 'AS BUILT', 'Projeto As-Built executado em campo', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'PROJETO CAD', 'Desenhos técnicos e arquivos CAD (DWG, DXF)', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'MEMORIAL DESCRITIVO', 'Memorial descritivo e especificações técnicas', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'RELATÓRIO FOTOGRÁFICO', 'Registro fotográfico das etapas da obra', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'ORÇAMENTO', 'Planilhas orçamentárias e levantamentos', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'OUTROS', 'Outros documentos e arquivos complementares', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (nome) DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════
-- Permissões IAM
-- ═══════════════════════════════════════════════════════════════════════

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:obra:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:obra:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:obra:import'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:obra:export'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:cadastros:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:cadastros:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:anexos:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:anexos:write')
ON CONFLICT (nome) DO NOTHING;

-- Administrador tem acesso total
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome IN (
    'obras:obra:read', 'obras:obra:write', 'obras:obra:import', 'obras:obra:export',
    'obras:cadastros:read', 'obras:cadastros:write',
    'obras:anexos:read', 'obras:anexos:write'
  )
ON CONFLICT DO NOTHING;

-- Supervisor tem leitura, escrita e exportação de obras e anexos
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'SUPERVISOR'
  AND perm.nome IN (
    'obras:obra:read', 'obras:obra:write', 'obras:obra:export',
    'obras:cadastros:read',
    'obras:anexos:read', 'obras:anexos:write'
  )
ON CONFLICT DO NOTHING;

-- Coordenador e Backoffice SESMT têm leitura
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome IN ('COORDENADOR_SESMT', 'BACKOFFICE_SESMT')
  AND perm.nome IN ('obras:obra:read', 'obras:anexos:read', 'obras:cadastros:read')
ON CONFLICT DO NOTHING;
