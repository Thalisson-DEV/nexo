-- =====================================================================
-- V173: Motor de metas de faturamento das equipes
--
-- A meta deixa de ser um número fixo na equipe (equipes.meta_faturamento_mensal)
-- e passa a ter cadastro próprio, com histórico e vigência:
--
-- * periodicidade CICLO_COMERCIAL: janela do dia 26 de um mês ao dia 25 do
--   seguinte (equipes do processo comercial); MENSAL: mês civil.
-- * a vigência é contada em janelas inteiras (começa no início de uma janela
--   e, quando tem fim, termina no fim de outra).
-- * duas metas ativas da mesma equipe não se sobrepõem (validado no domínio).
--
-- A equipe é referenciada sem FK, como nas metas de inspeção.
-- =====================================================================

CREATE TABLE metas_faturamento_equipe (
    id UUID PRIMARY KEY,
    id_equipe UUID NOT NULL,
    valor NUMERIC(12, 2) NOT NULL,
    periodicidade VARCHAR(20) NOT NULL,
    vigencia_inicio DATE NOT NULL,
    vigencia_fim DATE,
    descricao VARCHAR(200),

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_meta_faturamento_valor_positivo CHECK (valor > 0),
    CONSTRAINT ck_meta_faturamento_periodicidade CHECK (periodicidade IN ('CICLO_COMERCIAL', 'MENSAL')),
    CONSTRAINT ck_meta_faturamento_vigencia_coerente CHECK (
        vigencia_fim IS NULL OR vigencia_fim >= vigencia_inicio
    )
);

CREATE INDEX idx_metas_faturamento_equipe
    ON metas_faturamento_equipe(id_equipe)
    WHERE ativo = TRUE;

-- Carga: cada meta fixa vira uma meta sem prazo. Equipes do processo comercial
-- passam a apurar por ciclo; as demais seguem no mês civil. A vigência começa na
-- janela em que a equipe foi criada, para os recortes antigos continuarem com meta.
INSERT INTO metas_faturamento_equipe (
    id, id_equipe, valor, periodicidade, vigencia_inicio, descricao,
    versao, data_criacao, usuario_criacao, ativo
)
SELECT
    gen_random_uuid(),
    e.id,
    e.meta_faturamento_mensal,
    CASE WHEN p.nome ILIKE '%COMERCIAL%' THEN 'CICLO_COMERCIAL' ELSE 'MENSAL' END,
    CASE
        WHEN p.nome ILIKE '%COMERCIAL%' THEN
            CASE
                WHEN EXTRACT(DAY FROM (e.data_criacao AT TIME ZONE 'America/Sao_Paulo')) >= 26
                    THEN (date_trunc('month', e.data_criacao AT TIME ZONE 'America/Sao_Paulo')::date + 25)
                ELSE ((date_trunc('month', e.data_criacao AT TIME ZONE 'America/Sao_Paulo') - INTERVAL '1 month')::date + 25)
            END
        ELSE date_trunc('month', e.data_criacao AT TIME ZONE 'America/Sao_Paulo')::date
    END,
    'Migrada da meta mensal da equipe',
    0,
    CURRENT_TIMESTAMP,
    'SISTEMA',
    TRUE
FROM equipes e
JOIN processos_negocio p ON p.id = e.id_processo
WHERE e.meta_faturamento_mensal IS NOT NULL;

-- A coluna antiga fica sem uso pela aplicação; será removida numa migração futura,
-- depois que nenhuma instância anterior estiver no ar.
COMMENT ON COLUMN equipes.meta_faturamento_mensal IS
    'Obsoleta desde a V173: a meta vem de metas_faturamento_equipe.';

-- Permissões: quem já lê/edita equipes passa a ler/editar as metas.
INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'rh:meta-faturamento:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'rh:meta-faturamento:write')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pp.id_perfil, nova.id
FROM perfil_permissoes pp
JOIN permissoes atual ON atual.id = pp.id_permissao
JOIN permissoes nova ON nova.nome = 'rh:meta-faturamento:read'
WHERE atual.nome = 'rh:equipe:read'
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pp.id_perfil, nova.id
FROM perfil_permissoes pp
JOIN permissoes atual ON atual.id = pp.id_permissao
JOIN permissoes nova ON nova.nome IN ('rh:meta-faturamento:read', 'rh:meta-faturamento:write')
WHERE atual.nome = 'rh:equipe:write'
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome = 'ADMINISTRADOR'
  AND permissao.nome IN ('rh:meta-faturamento:read', 'rh:meta-faturamento:write')
ON CONFLICT DO NOTHING;
