-- Atualiza as views semânticas do Copiloto para expor tanto o processo de negócio da inspeção
-- quanto o processo de negócio do inspetor em SST.

DROP VIEW IF EXISTS copiloto.desvios CASCADE;
DROP VIEW IF EXISTS copiloto.inspecoes CASCADE;

CREATE OR REPLACE VIEW copiloto.inspecoes AS
SELECT i.id                                                      AS inspecao_id,
       i.data_realizacao,
       (i.data_realizacao AT TIME ZONE 'America/Sao_Paulo')::date AS data,
       date_trunc('month', i.data_realizacao AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       i.status,
       ti.nome                                                   AS tipo_inspecao,
       fi.titulo                                                 AS formulario,
       pn.nome                                                   AS processo,
       pn.nome                                                   AS processo_inspecao,
       pn_insp.nome                                              AS processo_inspetor,
       b.nome                                                    AS base_operacional,
       eq.cod_equipe                                             AS equipe,
       v.placa                                                   AS veiculo_placa,
       i.id_inspetor                                             AS inspetor_usuario_id,
       initcap(cin.nome_busca)                                   AS inspetor,
       (SELECT count(*) FROM public.desvios_seguranca d WHERE d.id_inspecao = i.id AND d.ativo = TRUE) AS total_desvios,
       (SELECT count(*) FROM public.inspecao_colaboradores ic WHERE ic.id_inspecao = i.id AND ic.ativo = TRUE) AS total_colaboradores_inspecionados
FROM public.inspecoes_seguranca i
LEFT JOIN public.tipos_inspecao ti ON ti.id = i.id_tipo_inspecao
LEFT JOIN public.formularios_inspecao fi ON fi.id = i.id_formulario
LEFT JOIN public.processos_negocio pn ON pn.id = i.id_processo
LEFT JOIN public.bases_operacionais b ON b.id = i.id_base_operacional
LEFT JOIN public.equipes eq ON eq.id = i.id_equipe
LEFT JOIN public.veiculos v ON v.id = i.id_veiculo
LEFT JOIN public.usuarios uin ON uin.id = i.id_inspetor
LEFT JOIN public.colaboradores cin ON cin.id = COALESCE(uin.id_colaborador, (SELECT c2.id FROM public.colaboradores c2 WHERE c2.id_usuario = i.id_inspetor LIMIT 1), (SELECT c3.id FROM public.colaboradores c3 WHERE c3.id = i.id_inspetor LIMIT 1))
LEFT JOIN public.processos_negocio pn_insp ON pn_insp.id = cin.id_processo
WHERE i.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.desvios AS
SELECT d.id                                                      AS desvio_id,
       d.id_inspecao                                             AS inspecao_id,
       i.data_realizacao                                         AS data_inspecao,
       date_trunc('month', i.data_realizacao AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       d.gravidade,
       d.status,
       d.prazo,
       d.data_resolucao,
       (d.status <> 'RESOLVIDO' AND d.prazo IS NOT NULL
           AND d.prazo < (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS prazo_vencido,
       (d.prazo - (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS dias_para_prazo,
       pq.enunciado                                              AS item_verificado,
       d.descricao,
       d.acao_corretiva,
       ti.nome                                                   AS tipo_inspecao,
       pn.nome                                                   AS processo,
       pn.nome                                                   AS processo_inspecao,
       pn_insp.nome                                              AS processo_inspetor,
       b.nome                                                    AS base_operacional,
       eq.cod_equipe                                             AS equipe,
       initcap(cin.nome_busca)                                   AS inspetor,
       (SELECT count(*) FROM public.desvio_colaboradores dc WHERE dc.id_desvio = d.id AND dc.ativo = TRUE) AS total_colaboradores_envolvidos
FROM public.desvios_seguranca d
JOIN public.inspecoes_seguranca i ON i.id = d.id_inspecao
LEFT JOIN public.perguntas_inspecao pq ON pq.id = d.id_pergunta
LEFT JOIN public.tipos_inspecao ti ON ti.id = i.id_tipo_inspecao
LEFT JOIN public.processos_negocio pn ON pn.id = i.id_processo
LEFT JOIN public.bases_operacionais b ON b.id = i.id_base_operacional
LEFT JOIN public.equipes eq ON eq.id = i.id_equipe
LEFT JOIN public.usuarios uin ON uin.id = i.id_inspetor
LEFT JOIN public.colaboradores cin ON cin.id = COALESCE(uin.id_colaborador, (SELECT c2.id FROM public.colaboradores c2 WHERE c2.id_usuario = i.id_inspetor LIMIT 1), (SELECT c3.id FROM public.colaboradores c3 WHERE c3.id = i.id_inspetor LIMIT 1))
LEFT JOIN public.processos_negocio pn_insp ON pn_insp.id = cin.id_processo
WHERE d.ativo = TRUE AND i.ativo = TRUE;
