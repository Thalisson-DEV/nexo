-- =====================================================================
-- V199: Planejamento da obra sobre o PDF do projeto
--
-- Coordenadas ficam em [0,1] relativas à página sem rotação: zoom e rotação
-- de exibição nunca alteram o dado. Cada entidade tem versão própria e é
-- gravada por comandos pequenos, para que edições simultâneas em partes
-- diferentes da obra não conflitem.
--
-- O item planejado guarda cópia de código, descrição, unidade e quantidade do
-- kit: mudar o catálogo depois não altera o que já foi planejado.
--
-- A análise do Copiloto é só uma proposta: fica em JSONB e só entra nas demais
-- tabelas quando o usuário aceita.
-- =====================================================================

CREATE TABLE planejamentos_obras (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_anexo_mestre UUID REFERENCES anexos_obras (id),
    rotacoes JSONB NOT NULL DEFAULT '{}'::jsonb,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_planejamentos_obras_obra ON planejamentos_obras (id_obra) WHERE ativo = TRUE;

CREATE TABLE pontos_planejamento (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    numero VARCHAR(20) NOT NULL,
    pagina INTEGER NOT NULL,
    x NUMERIC(8, 6) NOT NULL,
    y NUMERIC(8, 6) NOT NULL,
    barramento VARCHAR(60),
    tipo VARCHAR(20) NOT NULL DEFAULT 'POSTE',
    tipo_execucao VARCHAR(20) NOT NULL DEFAULT 'NAO_DEFINIDO',
    tem_poda BOOLEAN NOT NULL DEFAULT FALSE,
    tem_aterramento BOOLEAN NOT NULL DEFAULT FALSE,
    origem VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_pontos_planejamento_posicao CHECK (x BETWEEN 0 AND 1 AND y BETWEEN 0 AND 1 AND pagina >= 1),
    CONSTRAINT ck_pontos_planejamento_tipo CHECK (tipo IN ('POSTE', 'TRANSFORMADOR', 'CHAVE', 'ESTRUTURA', 'OUTRO')),
    CONSTRAINT ck_pontos_planejamento_execucao
        CHECK (tipo_execucao IN ('NAO_DEFINIDO', 'SEM_DESLIGAMENTO', 'COM_DESLIGAMENTO')),
    CONSTRAINT ck_pontos_planejamento_origem CHECK (origem IN ('MANUAL', 'COPILOTO'))
);

CREATE UNIQUE INDEX uq_pontos_planejamento_numero ON pontos_planejamento (id_obra, numero) WHERE ativo = TRUE;
CREATE INDEX idx_pontos_planejamento_obra_pagina ON pontos_planejamento (id_obra, pagina);

CREATE TABLE vaos_planejamento (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_ponto_a UUID NOT NULL REFERENCES pontos_planejamento (id),
    id_ponto_b UUID NOT NULL REFERENCES pontos_planejamento (id),
    metragem NUMERIC(10, 2),
    origem VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_vaos_planejamento_pontos CHECK (id_ponto_a <> id_ponto_b),
    CONSTRAINT ck_vaos_planejamento_metragem CHECK (metragem IS NULL OR metragem > 0),
    CONSTRAINT ck_vaos_planejamento_origem CHECK (origem IN ('MANUAL', 'COPILOTO'))
);

CREATE UNIQUE INDEX uq_vaos_planejamento_par
    ON vaos_planejamento (id_obra, LEAST(id_ponto_a, id_ponto_b), GREATEST(id_ponto_a, id_ponto_b))
    WHERE ativo = TRUE;
CREATE INDEX idx_vaos_planejamento_obra ON vaos_planejamento (id_obra);

CREATE TABLE kits_aplicados (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_ponto UUID REFERENCES pontos_planejamento (id),
    id_vao UUID REFERENCES vaos_planejamento (id),
    id_kit UUID NOT NULL REFERENCES kits_construtivos (id),
    id_contrato UUID NOT NULL,
    codigo_kit VARCHAR(40) NOT NULL,
    descricao_kit VARCHAR(300) NOT NULL,
    grupo_kit VARCHAR(60),
    operacao VARCHAR(20) NOT NULL,
    multiplicador NUMERIC(14, 4) NOT NULL,
    executado_antes BOOLEAN NOT NULL DEFAULT FALSE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_kits_aplicados_alvo CHECK ((id_ponto IS NULL) <> (id_vao IS NULL)),
    CONSTRAINT ck_kits_aplicados_operacao CHECK (operacao IN ('INSTALAR', 'RETIRAR')),
    CONSTRAINT ck_kits_aplicados_multiplicador CHECK (multiplicador > 0)
);

CREATE UNIQUE INDEX uq_kits_aplicados_ponto ON kits_aplicados (id_ponto, id_kit, operacao)
    WHERE ativo = TRUE AND id_ponto IS NOT NULL;
CREATE UNIQUE INDEX uq_kits_aplicados_vao ON kits_aplicados (id_vao, id_kit, operacao)
    WHERE ativo = TRUE AND id_vao IS NOT NULL;
CREATE INDEX idx_kits_aplicados_obra ON kits_aplicados (id_obra);

CREATE TABLE itens_planejados (
    id UUID PRIMARY KEY,
    id_kit_aplicado UUID NOT NULL REFERENCES kits_aplicados (id),
    tipo VARCHAR(20) NOT NULL,
    id_acao UUID,
    id_material UUID,
    codigo VARCHAR(60),
    descricao VARCHAR(300),
    operacao VARCHAR(20) NOT NULL,
    unidade VARCHAR(20),
    quantidade_kit NUMERIC(14, 4) NOT NULL,
    quantidade_prevista NUMERIC(14, 4) NOT NULL,
    quantidade_executada NUMERIC(14, 4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_itens_planejados_tipo CHECK (tipo IN ('SERVICO', 'MATERIAL')),
    CONSTRAINT ck_itens_planejados_operacao CHECK (operacao IN ('INSTALAR', 'RETIRAR')),
    CONSTRAINT ck_itens_planejados_status
        CHECK (status IN ('PENDENTE', 'PROGRAMADO', 'EM_ANDAMENTO', 'CONCLUIDO', 'MEDIDO')),
    CONSTRAINT ck_itens_planejados_quantidade CHECK (quantidade_prevista > 0 AND quantidade_executada >= 0)
);

CREATE INDEX idx_itens_planejados_kit ON itens_planejados (id_kit_aplicado);
CREATE INDEX idx_itens_planejados_acao ON itens_planejados (id_acao);

CREATE TABLE anotacoes_planejamento (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    pagina INTEGER NOT NULL,
    x NUMERIC(8, 6) NOT NULL,
    y NUMERIC(8, 6) NOT NULL,
    texto VARCHAR(1000) NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_anotacoes_planejamento_posicao CHECK (x BETWEEN 0 AND 1 AND y BETWEEN 0 AND 1 AND pagina >= 1)
);

CREATE INDEX idx_anotacoes_planejamento_obra ON anotacoes_planejamento (id_obra);

CREATE TABLE analises_planejamento (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_anexo UUID NOT NULL REFERENCES anexos_obras (id),
    pagina INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    proposta JSONB,
    modelo VARCHAR(200),
    mensagem VARCHAR(1000),
    data_conclusao TIMESTAMP WITH TIME ZONE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_analises_planejamento_status CHECK (status IN ('PROCESSANDO', 'CONCLUIDA', 'FALHOU'))
);

CREATE INDEX idx_analises_planejamento_obra ON analises_planejamento (id_obra, data_criacao DESC);
