-- Lançamento de nota em etapa única: a execução comercial passa a ter uma só data
-- (a do lançamento) e uma só localização. Não existe mais início e fim.
--
--   data_inicio      -> data_lancamento (preserva o valor: é a data que já definia o
--                       período de faturamento e o preço vigente das ações)
--   latitude_inicio  -> latitude
--   longitude_inicio -> longitude
--   data_fim, latitude_fim, longitude_fim -> removidas
--
-- Destrutiva para registros antigos de duas etapas: o horário de conclusão e a
-- localização de fim deixam de existir. Lançamentos feitos pelo app atual já
-- gravavam data_fim igual a data_inicio e não tinham localização de fim.
--
-- As views do Copiloto que leem estas colunas são recriadas no mesmo script
-- (regra de manutenção da V152).

DROP VIEW IF EXISTS copiloto.execucoes_comerciais;
DROP VIEW IF EXISTS copiloto.execucoes_acoes;
DROP VIEW IF EXISTS copiloto.execucoes_materiais;

ALTER TABLE execucoes_comercial RENAME COLUMN data_inicio TO data_lancamento;
ALTER TABLE execucoes_comercial RENAME COLUMN latitude_inicio TO latitude;
ALTER TABLE execucoes_comercial RENAME COLUMN longitude_inicio TO longitude;

ALTER TABLE execucoes_comercial
    DROP COLUMN data_fim,
    DROP COLUMN latitude_fim,
    DROP COLUMN longitude_fim;

-- Os dashboards operacional e de turnos agregam por equipe e por dia de lançamento
-- (o agrupamento por turno já é atendido por idx_execucoes_comercial_turno, V31).
CREATE INDEX IF NOT EXISTS idx_execucoes_comercial_equipe_lancamento
    ON execucoes_comercial (id_equipe, data_lancamento)
    WHERE ativo = TRUE;

COMMENT ON COLUMN execucoes_comercial.data_lancamento IS 'Momento em que a nota foi lançada (lançamento em etapa única).';

CREATE VIEW copiloto.execucoes_comerciais AS
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
       (SELECT count(*) FROM public.execucoes_materiais em WHERE em.id_execucao = ex.id AND em.ativo = TRUE) AS total_materiais
FROM public.execucoes_comercial ex
LEFT JOIN public.equipes eq ON eq.id = ex.id_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
LEFT JOIN public.municipios mu ON mu.id = ex.id_municipio
WHERE ex.ativo = TRUE;

CREATE VIEW copiloto.execucoes_acoes AS
SELECT ea.id                                                   AS execucao_acao_id,
       ex.id                                                   AS execucao_id,
       ex.data_lancamento,
       date_trunc('month', ex.data_lancamento AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       eq.cod_equipe                                           AS equipe,
       a.cod_acao                                              AS acao_codigo,
       a.nome                                                  AS acao,
       ex.status_execucao
FROM public.execucoes_acoes ea
JOIN public.execucoes_comercial ex ON ex.id = ea.id_execucao
JOIN public.acoes a ON a.id = ea.id_acao
LEFT JOIN public.equipes eq ON eq.id = ex.id_equipe
WHERE ea.ativo = TRUE AND ex.ativo = TRUE;

CREATE VIEW copiloto.execucoes_materiais AS
SELECT em.id                                                   AS execucao_material_id,
       ex.id                                                   AS execucao_id,
       ex.data_lancamento,
       date_trunc('month', ex.data_lancamento AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       eq.cod_equipe                                           AS equipe,
       m.cod_material                                          AS material_codigo,
       m.descricao                                             AS material,
       m.unidade_medida,
       em.quantidade,
       em.operacao
FROM public.execucoes_materiais em
JOIN public.execucoes_comercial ex ON ex.id = em.id_execucao
JOIN public.materiais m ON m.id = em.id_material
LEFT JOIN public.equipes eq ON eq.id = ex.id_equipe
WHERE em.ativo = TRUE AND ex.ativo = TRUE;
