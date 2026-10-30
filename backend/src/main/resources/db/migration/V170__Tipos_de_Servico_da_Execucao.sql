-- =====================================================================
-- V170: Tipos de serviço das notas executadas em campo
--
-- O encarregado passa a informar, junto com o número da nota, o tipo de
-- serviço que está executando (Ligação nova, Corte, Religação...).
--
-- O código é a classificação da nota e se repete entre tipos (Modificação,
-- Alteração e Enlace são todos CM); quem identifica o tipo é a descrição,
-- única entre os tipos ativos.
--
-- As execuções já lançadas ficam sem tipo ("não informado"): a coluna é
-- opcional no banco e a obrigatoriedade vale no app de campo.
-- =====================================================================

CREATE TABLE tipos_servico (
    id UUID PRIMARY KEY,
    codigo VARCHAR(10) NOT NULL,
    descricao VARCHAR(120) NOT NULL,
    descricao_normalizada VARCHAR(120) NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX ux_tipos_servico_descricao_ativa
    ON tipos_servico (descricao_normalizada) WHERE ativo;

CREATE INDEX ix_tipos_servico_codigo ON tipos_servico (codigo);

INSERT INTO tipos_servico (id, codigo, descricao, descricao_normalizada, versao, data_criacao, usuario_criacao, ativo)
VALUES
    ('7e500000-0000-4000-8000-000000000001', 'CA', 'ACOMPANHAMENTO',  'acompanhamento',  0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-000000000002', 'CV', 'VERIFICAÇÃO',     'verificacao',     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-000000000003', 'CM', 'MODIFICAÇÃO',     'modificacao',     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-000000000004', 'CM', 'ALTERAÇÃO',       'alteracao',       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-000000000005', 'CI', 'INSPEÇÃO',        'inspecao',        0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-000000000006', 'CR', 'RELIGAÇÃO',       'religacao',       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-000000000007', 'CL', 'LIGAÇÃO NOVA',    'ligacao nova',    0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-000000000008', 'CB', 'BAIXA A PEDIDO',  'baixa a pedido',  0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-000000000009', 'CB', 'BAIXA ADM',       'baixa adm',       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-00000000000a', 'CL', 'VARREDURA',       'varredura',       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-00000000000b', 'CV', 'MICROGERAÇÃO',    'microgeracao',    0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-00000000000c', 'CL', 'REATIVAÇÃO',      'reativacao',      0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-00000000000d', 'CM', 'ENLACE',          'enlace',          0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('7e500000-0000-4000-8000-00000000000e', 'CS', 'CORTE',           'corte',           0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

-- Vínculo da execução com o tipo de serviço
ALTER TABLE execucoes_comercial
    ADD COLUMN id_tipo_servico UUID;

ALTER TABLE execucoes_comercial
    ADD CONSTRAINT fk_execucoes_comercial_tipo_servico
        FOREIGN KEY (id_tipo_servico) REFERENCES tipos_servico (id);

CREATE INDEX ix_execucoes_comercial_tipo_servico ON execucoes_comercial (id_tipo_servico);

-- Permissões do cadastro. A leitura das opções no lançamento já é coberta por
-- execucao:comercial:read/write; o cadastro em si fica com o administrador, e
-- quem já consulta execuções também pode consultar a lista de tipos.
INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'execucao:tipo-servico:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'execucao:tipo-servico:write')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome IN ('execucao:tipo-servico:read', 'execucao:tipo-servico:write')
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT DISTINCT pp.id_perfil, nova.id
FROM perfil_permissoes pp
JOIN permissoes existente ON existente.id = pp.id_permissao AND existente.nome = 'execucao:comercial:read'
CROSS JOIN permissoes nova
WHERE nova.nome = 'execucao:tipo-servico:read'
ON CONFLICT DO NOTHING;

-- View do Copiloto (regra de manutenção da V152): o tipo de serviço entra no fim
-- da lista de colunas, o que permite o CREATE OR REPLACE sem recriar a view.
CREATE OR REPLACE VIEW copiloto.execucoes_comerciais AS
SELECT ex.id                                                   AS execucao_id,
       ex.status_execucao,
       ex.data_lancamento,
       (ex.data_lancamento AT TIME ZONE 'America/Sao_Paulo')::date AS data,
       date_trunc('month', ex.data_lancamento AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       eq.cod_equipe                                           AS equipe,
       b.nome                                                  AS base_operacional,
       mu.nome                                                 AS municipio,
       ex.valor_total_faturado,
       ex.id_turno                                             AS turno_id,
       (SELECT count(*) FROM public.execucoes_acoes ea WHERE ea.id_execucao = ex.id AND ea.ativo = TRUE) AS total_acoes,
       (SELECT count(*) FROM public.execucoes_materiais em WHERE em.id_execucao = ex.id AND em.ativo = TRUE) AS total_materiais,
       ts.codigo                                               AS tipo_servico_codigo,
       COALESCE(ts.descricao, 'NÃO INFORMADO')                 AS tipo_servico
FROM public.execucoes_comercial ex
LEFT JOIN public.equipes eq ON eq.id = ex.id_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
LEFT JOIN public.municipios mu ON mu.id = ex.id_municipio
LEFT JOIN public.tipos_servico ts ON ts.id = ex.id_tipo_servico
WHERE ex.ativo = TRUE;
