-- Relatório do comercial por base: o Copiloto precisa filtrar ações e materiais das
-- execuções pela base da equipe e enxergar as metas de faturamento (V173).
--
-- * execucoes_acoes e execucoes_materiais ganham base_operacional no fim (CREATE OR
--   REPLACE VIEW só acrescenta colunas no fim; as demais ficam como na V168).
-- * metas_faturamento: as metas cadastradas por equipe, com base, processo,
--   periodicidade, vigência e se vale hoje. CICLO_COMERCIAL vai do dia 26 ao dia 25 do
--   mês seguinte; MENSAL é o mês civil.

CREATE OR REPLACE VIEW copiloto.execucoes_acoes AS
SELECT ea.id                                                   AS execucao_acao_id,
       ex.id                                                   AS execucao_id,
       ex.data_lancamento,
       date_trunc('month', ex.data_lancamento AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       eq.cod_equipe                                           AS equipe,
       a.cod_acao                                              AS acao_codigo,
       a.nome                                                  AS acao,
       ex.status_execucao,
       b.nome                                                  AS base_operacional
FROM public.execucoes_acoes ea
JOIN public.execucoes_comercial ex ON ex.id = ea.id_execucao
JOIN public.acoes a ON a.id = ea.id_acao
LEFT JOIN public.equipes eq ON eq.id = ex.id_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
WHERE ea.ativo = TRUE AND ex.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.execucoes_materiais AS
SELECT em.id                                                   AS execucao_material_id,
       ex.id                                                   AS execucao_id,
       ex.data_lancamento,
       date_trunc('month', ex.data_lancamento AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       eq.cod_equipe                                           AS equipe,
       m.cod_material                                          AS material_codigo,
       m.descricao                                             AS material,
       m.unidade_medida,
       em.quantidade,
       em.operacao,
       b.nome                                                  AS base_operacional
FROM public.execucoes_materiais em
JOIN public.execucoes_comercial ex ON ex.id = em.id_execucao
JOIN public.materiais m ON m.id = em.id_material
LEFT JOIN public.equipes eq ON eq.id = ex.id_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
WHERE em.ativo = TRUE AND ex.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.metas_faturamento AS
SELECT mf.id                                                   AS meta_id,
       eq.cod_equipe                                           AS equipe,
       b.nome                                                  AS base_operacional,
       pn.nome                                                 AS processo,
       mf.periodicidade,
       mf.valor                                                AS meta_valor,
       mf.vigencia_inicio,
       mf.vigencia_fim,
       mf.descricao,
       (mf.vigencia_inicio <= (now() AT TIME ZONE 'America/Sao_Paulo')::date
         AND (mf.vigencia_fim IS NULL OR mf.vigencia_fim >= (now() AT TIME ZONE 'America/Sao_Paulo')::date)) AS vigente
FROM public.metas_faturamento_equipe mf
JOIN public.equipes eq ON eq.id = mf.id_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
LEFT JOIN public.processos_negocio pn ON pn.id = eq.id_processo
WHERE mf.ativo = TRUE;
