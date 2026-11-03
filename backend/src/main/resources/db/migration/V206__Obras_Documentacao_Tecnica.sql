-- APR por serviço do dia: a equipe lança uma ou mais ao longo do dia, e cada uma libera a sua programação.
ALTER TABLE turno_aprs ADD COLUMN id_programacao UUID REFERENCES programacoes (id);
CREATE INDEX idx_turno_aprs_programacao ON turno_aprs (id_programacao) WHERE id_programacao IS NOT NULL;

-- Documentação técnica da obra: modelos configuráveis de checklist, visita prévia, fichas As Built, documentos gerados e book.
CREATE TABLE modelos_checklist_obra (
    id UUID PRIMARY KEY,
    finalidade VARCHAR(20) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descricao VARCHAR(500),
    estrutura JSONB NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_modelos_checklist_obra_finalidade CHECK (finalidade IN ('VISITA_PREVIA', 'AS_BUILT'))
);

CREATE UNIQUE INDEX uq_modelos_checklist_obra_titulo ON modelos_checklist_obra (finalidade, LOWER(titulo)) WHERE ativo = TRUE;

CREATE TABLE visitas_previas_obra (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_modelo UUID NOT NULL REFERENCES modelos_checklist_obra (id),
    titulo_modelo VARCHAR(150) NOT NULL,
    data_visita DATE NOT NULL,
    responsavel VARCHAR(150) NOT NULL,
    observacoes VARCHAR(2000),
    respostas JSONB NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_visitas_previas_obra ON visitas_previas_obra (id_obra) WHERE ativo = TRUE;
CREATE INDEX idx_visitas_previas_modelo ON visitas_previas_obra (id_modelo);

CREATE TABLE fichas_as_built (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_ponto UUID NOT NULL REFERENCES pontos_planejamento (id),
    id_modelo UUID NOT NULL REFERENCES modelos_checklist_obra (id),
    titulo_modelo VARCHAR(150) NOT NULL,
    respostas JSONB NOT NULL,
    completa BOOLEAN NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_fichas_as_built_ponto ON fichas_as_built (id_ponto) WHERE ativo = TRUE;
CREATE INDEX idx_fichas_as_built_obra ON fichas_as_built (id_obra) WHERE ativo = TRUE;
CREATE INDEX idx_fichas_as_built_modelo ON fichas_as_built (id_modelo);

CREATE TABLE documentos_as_built (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    numero_versao INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    id_anexo UUID REFERENCES anexos_obras (id),
    mensagem_erro VARCHAR(500),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_documentos_as_built_status CHECK (status IN ('PROCESSANDO', 'CONCLUIDO', 'ERRO'))
);

-- Tentativa que falhou não consome o número da versão.
CREATE UNIQUE INDEX uq_documentos_as_built_versao ON documentos_as_built (id_obra, numero_versao) WHERE status <> 'ERRO';

CREATE TABLE equipamentos_obra (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_ponto UUID NOT NULL REFERENCES pontos_planejamento (id),
    tipo VARCHAR(20) NOT NULL,
    trafo_auxiliar BOOLEAN NOT NULL DEFAULT FALSE,
    observacao VARCHAR(500),
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_equipamentos_obra_tipo CHECK (tipo IN ('RELIGADOR', 'REGULADOR')),
    CONSTRAINT ck_equipamentos_obra_trafo CHECK (tipo = 'RELIGADOR' OR trafo_auxiliar = FALSE)
);

CREATE UNIQUE INDEX uq_equipamentos_obra_ponto ON equipamentos_obra (id_ponto) WHERE ativo = TRUE;
CREATE INDEX idx_equipamentos_obra_obra ON equipamentos_obra (id_obra) WHERE ativo = TRUE;

-- Quem configura os modelos é o administrador; a escrita de obras:documentacao já é de administrador e supervisor (V198).
INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:documentacao:manage')
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome = 'obras:documentacao:manage'
ON CONFLICT DO NOTHING;
