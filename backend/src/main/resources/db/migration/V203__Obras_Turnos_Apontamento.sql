-- =====================================================================
-- V203: Turnos de Obras e apontamento da execução
--
-- O turno continua sendo o do módulo de execução. Líder e substitutos são
-- opcionais: o turno aberto pelo app não os informa e nada muda no comercial.
--
-- apontamentos_execucao é a única origem de quantidade_executada dos itens
-- planejados. O valor fica congelado com o da programação, e o estorno
-- desativa o apontamento e devolve a quantidade ao item.
--
-- Atividades, relatório, ocorrências, APRs e anexos ficam fora da tabela de
-- turnos: só equipes de Obras os usam. O app de campo gravará nas mesmas
-- tabelas quando a execução chegar lá.
-- =====================================================================

ALTER TABLE turnos ADD COLUMN id_lider UUID;

CREATE TABLE turno_substitutos (
    id_turno UUID NOT NULL REFERENCES turnos (id),
    id_colaborador UUID NOT NULL,
    PRIMARY KEY (id_turno, id_colaborador)
);

CREATE TABLE apontamentos_execucao (
    id UUID PRIMARY KEY,
    id_item_planejado UUID NOT NULL REFERENCES itens_planejados (id),
    id_programacao UUID NOT NULL REFERENCES programacoes (id),
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_equipe UUID NOT NULL REFERENCES equipes (id),
    id_turno UUID REFERENCES turnos (id),
    data DATE NOT NULL,
    quantidade NUMERIC(14, 4) NOT NULL,
    excedente NUMERIC(14, 4) NOT NULL DEFAULT 0,
    valor_total NUMERIC(15, 2),
    justificativa VARCHAR(1000),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_apontamentos_quantidade CHECK (quantidade > 0),
    CONSTRAINT ck_apontamentos_excedente CHECK (excedente = 0 OR justificativa IS NOT NULL)
);

CREATE INDEX idx_apontamentos_turno ON apontamentos_execucao (id_turno) WHERE ativo = TRUE;
CREATE INDEX idx_apontamentos_programacao ON apontamentos_execucao (id_programacao) WHERE ativo = TRUE;
CREATE INDEX idx_apontamentos_equipe_data ON apontamentos_execucao (id_equipe, data) WHERE ativo = TRUE;
CREATE INDEX idx_apontamentos_obra ON apontamentos_execucao (id_obra) WHERE ativo = TRUE;

CREATE TABLE turno_atividades (
    id UUID PRIMARY KEY,
    id_turno UUID NOT NULL REFERENCES turnos (id),
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_programacao UUID REFERENCES programacoes (id),
    observacao VARCHAR(1000),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_turno_atividades_turno ON turno_atividades (id_turno) WHERE ativo = TRUE;
CREATE INDEX idx_turno_atividades_obra ON turno_atividades (id_obra) WHERE ativo = TRUE;

CREATE TABLE turno_relatorios (
    id UUID PRIMARY KEY,
    id_turno UUID NOT NULL REFERENCES turnos (id),
    observacao VARCHAR(4000) NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_turno_relatorios_turno UNIQUE (id_turno)
);

CREATE TABLE turno_ocorrencias (
    id UUID PRIMARY KEY,
    id_turno UUID NOT NULL REFERENCES turnos (id),
    id_obra UUID REFERENCES obras (id),
    tipo VARCHAR(30) NOT NULL,
    descricao VARCHAR(2000) NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_turno_ocorrencias_tipo CHECK (tipo IN ('SEGURANCA', 'CLIMA', 'MATERIAL', 'EQUIPAMENTO', 'ACESSO', 'DESLIGAMENTO', 'TERCEIROS', 'OUTROS'))
);

CREATE INDEX idx_turno_ocorrencias_turno ON turno_ocorrencias (id_turno) WHERE ativo = TRUE;

CREATE TABLE turno_aprs (
    id UUID PRIMARY KEY,
    id_turno UUID NOT NULL REFERENCES turnos (id),
    id_obra UUID REFERENCES obras (id),
    atividade VARCHAR(500) NOT NULL,
    id_responsavel UUID,
    status VARCHAR(20) NOT NULL,
    data_conclusao TIMESTAMP WITH TIME ZONE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_turno_aprs_status CHECK (status IN ('RASCUNHO', 'CONCLUIDA'))
);

CREATE INDEX idx_turno_aprs_turno ON turno_aprs (id_turno) WHERE ativo = TRUE;

CREATE TABLE turno_anexos (
    id UUID PRIMARY KEY,
    id_turno UUID NOT NULL REFERENCES turnos (id),
    id_apr UUID REFERENCES turno_aprs (id),
    nome_arquivo VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    chave_storage VARCHAR(500) NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_turno_anexos_turno ON turno_anexos (id_turno) WHERE ativo = TRUE;

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:turno:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:turno:write')
ON CONFLICT (nome) DO NOTHING;

-- Quem já programa ou aponta em Obras passa a ver os turnos; só quem aponta os registra.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT DISTINCT pp.id_perfil, nova.id
FROM perfil_permissoes pp
JOIN permissoes atual ON atual.id = pp.id_permissao
JOIN permissoes nova ON nova.nome = 'obras:turno:read'
WHERE atual.nome IN ('obras:programacao:read', 'obras:execucao:write')
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT DISTINCT pp.id_perfil, nova.id
FROM perfil_permissoes pp
JOIN permissoes atual ON atual.id = pp.id_permissao
JOIN permissoes nova ON nova.nome = 'obras:turno:write'
WHERE atual.nome = 'obras:execucao:write'
ON CONFLICT DO NOTHING;
