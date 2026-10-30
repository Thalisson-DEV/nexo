-- Camada semantica SQL do Copiloto (somente leitura) cobrindo todos os modulos do Nexo:
-- almoxarifado, obras, RH, execucao, contratos, descontos, financeiro, SST, chamados,
-- comunicacao/notificacoes, frota, IAM e sincronizacao offline.
--
-- O agente analitico so pode consultar as views deste schema. Elas:
--   * expoem nomes de negocio (material, base_operacional, obra_numero...) em vez de FKs cruas;
--   * nunca expoem colunas sensiveis (CPF, login, e-mail, telefone, senha, assinaturas, nomes e
--     textos cifrados, dados bancarios/de pagamento, chaves de acesso, observacoes e justificativas
--     livres, hashes, snapshots de auditoria e payloads brutos);
--   * aplicam soft delete (ativo = TRUE) quando a tela correspondente tambem aplica;
--   * derivam campos de negocio (mes, prazos, SLA, situacao) no fuso America/Sao_Paulo.
-- Cada view e associada a permissoes do Nexo em copiloto/semantic-catalog.yaml; o backend valida
-- as permissoes do usuario para toda view referenciada antes de executar o SQL.
-- Escopo por usuario (ex.: "meus chamados", "minhas notificacoes") usa os parametros de sessao
-- copiloto.usuario_id e copiloto.escopo_chamados, definidos pelo backend a cada consulta.
--
-- Manutencao: uma migration que remova ou renomeie colunas usadas aqui deve recriar a view
-- correspondente (CREATE OR REPLACE VIEW) no mesmo script.

CREATE SCHEMA IF NOT EXISTS copiloto;

-- =============================================================================================
-- ALMOXARIFADO / ESTOQUE
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.materiais AS
SELECT m.id                AS material_id,
       m.cod_material      AS material_codigo,
       m.descricao         AS material,
       m.unidade_medida,
       m.detalha_serie     AS controla_numero_serie,
       pn.nome             AS processo,
       av.nome             AS avaliacao_tecnica,
       ci.codigo           AS centro_investimento,
       m.ativo,
       m.data_criacao      AS criado_em
FROM public.materiais m
LEFT JOIN public.processos_negocio pn ON pn.id = m.id_processo
LEFT JOIN public.avaliacoes_material av ON av.id = m.id_avaliacao_tecnica
LEFT JOIN public.centros_investimento ci ON ci.id = m.id_centro_investimento;

CREATE OR REPLACE VIEW copiloto.estoques AS
SELECT e.id                                   AS estoque_id,
       e.nome_identificador                   AS estoque,
       te.nome                                AS tipo_estoque,
       COALESCE(b.id, beq.id)                 AS base_operacional_id,
       COALESCE(b.nome, beq.nome)             AS base_operacional,
       eq.id                                  AS equipe_id,
       eq.cod_equipe                          AS equipe,
       o.id                                   AS obra_id,
       o.numero                               AS obra_numero,
       e.ativo
FROM public.estoques e
LEFT JOIN public.tipos_estoque te ON te.id = e.id_tipo_estoque
LEFT JOIN public.bases_operacionais b ON b.id = e.id_base_operacional
LEFT JOIN public.equipes eq ON eq.id = e.id_equipe
LEFT JOIN public.bases_operacionais beq ON beq.id = eq.id_base_operacional
LEFT JOIN public.obras o ON o.id = e.id_obra;

CREATE OR REPLACE VIEW copiloto.saldos AS
SELECT s.id                                          AS saldo_id,
       s.id_estoque                                  AS estoque_id,
       e.nome_identificador                          AS estoque,
       te.nome                                       AS tipo_estoque,
       COALESCE(b.nome, beq.nome)                    AS base_operacional,
       eq.cod_equipe                                 AS equipe,
       s.id_material                                 AS material_id,
       m.cod_material                                AS material_codigo,
       m.descricao                                   AS material,
       m.unidade_medida,
       s.condicao,
       s.quantidade,
       COALESCE(s.data_ultima_alteracao, s.data_criacao) AS atualizado_em
FROM public.saldos s
JOIN public.estoques e ON e.id = s.id_estoque
JOIN public.materiais m ON m.id = s.id_material
LEFT JOIN public.tipos_estoque te ON te.id = e.id_tipo_estoque
LEFT JOIN public.bases_operacionais b ON b.id = e.id_base_operacional
LEFT JOIN public.equipes eq ON eq.id = e.id_equipe
LEFT JOIN public.bases_operacionais beq ON beq.id = eq.id_base_operacional
WHERE s.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.saldos_seriais AS
SELECT ss.id                        AS saldo_serial_id,
       ss.id_estoque                AS estoque_id,
       e.nome_identificador         AS estoque,
       COALESCE(b.nome, beq.nome)   AS base_operacional,
       eq.cod_equipe                AS equipe,
       ss.id_material               AS material_id,
       m.cod_material               AS material_codigo,
       m.descricao                  AS material,
       ss.num_serie                 AS numero_serie,
       ss.condicao,
       COALESCE(ss.data_ultima_alteracao, ss.data_criacao) AS atualizado_em
FROM public.saldos_seriais ss
JOIN public.estoques e ON e.id = ss.id_estoque
JOIN public.materiais m ON m.id = ss.id_material
LEFT JOIN public.bases_operacionais b ON b.id = e.id_base_operacional
LEFT JOIN public.equipes eq ON eq.id = e.id_equipe
LEFT JOIN public.bases_operacionais beq ON beq.id = eq.id_base_operacional
WHERE ss.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.movimentacoes AS
SELECT mv.id                                                        AS movimentacao_id,
       mv.data_hora,
       (mv.data_hora AT TIME ZONE 'America/Sao_Paulo')::date        AS data,
       date_trunc('month', mv.data_hora AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       mv.tipo_movimentacao,
       mv.id_material                                               AS material_id,
       m.cod_material                                               AS material_codigo,
       m.descricao                                                  AS material,
       m.unidade_medida,
       mv.quantidade,
       mv.condicao,
       mv.num_serie                                                 AS numero_serie,
       mv.id_estoque_origem                                         AS estoque_origem_id,
       eo.nome_identificador                                        AS estoque_origem,
       COALESCE(bo.nome, beo.nome)                                  AS base_origem,
       mv.id_estoque_destino                                        AS estoque_destino_id,
       ed.nome_identificador                                        AS estoque_destino,
       COALESCE(bd.nome, bed.nome)                                  AS base_destino,
       mv.id_obra                                                   AS obra_id,
       o.numero                                                     AS obra_numero,
       mv.id_equipe                                                 AS equipe_id,
       eq.cod_equipe                                                AS equipe,
       mv.id_solicitacao                                            AS solicitacao_id,
       mv.id_reserva                                                AS reserva_id,
       CASE mv.tipo_tecnico
           WHEN '221' THEN 'ADICAO_COMUM'
           WHEN '921' THEN 'ADICAO_CONCRETO'
           WHEN '222' THEN 'DEVOLUCAO_COMUM'
           WHEN '922' THEN 'DEVOLUCAO_CONCRETO'
       END                                                          AS tipo_tecnico,
       mv.tipo_obra_snapshot                                        AS tipo_obra,
       mv.codigo_ocorrencia_snapshot                                AS ocorrencia,
       mv.id_referencia_operacao                                    AS operacao_id,
       mv.id_usuario_operador                                       AS operador_usuario_id,
       initcap(cop.nome_busca)                                      AS operador
FROM public.movimentacoes_estoque mv
JOIN public.materiais m ON m.id = mv.id_material
LEFT JOIN public.estoques eo ON eo.id = mv.id_estoque_origem
LEFT JOIN public.bases_operacionais bo ON bo.id = eo.id_base_operacional
LEFT JOIN public.equipes eqo ON eqo.id = eo.id_equipe
LEFT JOIN public.bases_operacionais beo ON beo.id = eqo.id_base_operacional
LEFT JOIN public.estoques ed ON ed.id = mv.id_estoque_destino
LEFT JOIN public.bases_operacionais bd ON bd.id = ed.id_base_operacional
LEFT JOIN public.equipes eqd ON eqd.id = ed.id_equipe
LEFT JOIN public.bases_operacionais bed ON bed.id = eqd.id_base_operacional
LEFT JOIN public.obras o ON o.id = mv.id_obra
LEFT JOIN public.equipes eq ON eq.id = mv.id_equipe
LEFT JOIN public.usuarios uop ON uop.id = mv.id_usuario_operador
LEFT JOIN public.colaboradores cop ON cop.id = uop.id_colaborador
WHERE mv.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.solicitacoes AS
SELECT s.id                                                          AS solicitacao_id,
       s.contexto,
       CASE s.tipo_tecnico
           WHEN '221' THEN 'ADICAO_COMUM'
           WHEN '921' THEN 'ADICAO_CONCRETO'
           WHEN '222' THEN 'DEVOLUCAO_COMUM'
           WHEN '922' THEN 'DEVOLUCAO_CONCRETO'
       END                                                           AS tipo_tecnico,
       s.status,
       s.status_separacao,
       s.data_solicitacao,
       (s.data_solicitacao AT TIME ZONE 'America/Sao_Paulo')::date   AS data,
       date_trunc('month', s.data_solicitacao AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       s.data_verificacao                                            AS data_decisao,
       s.data_entrega,
       s.data_prevista_retirada,
       s.separacao_iniciada_em,
       s.separacao_concluida_em,
       s.encerrada_em,
       (s.data_prevista_retirada IS NOT NULL
           AND s.data_prevista_retirada < (s.data_solicitacao AT TIME ZONE 'America/Sao_Paulo')::date + 2)
                                                                     AS fora_prazo_saque,
       (s.status IN ('PENDENTE', 'APROVADO')
           AND s.data_prevista_retirada IS NOT NULL
           AND s.data_prevista_retirada < (now() AT TIME ZONE 'America/Sao_Paulo')::date)
                                                                     AS retirada_atrasada,
       CASE WHEN s.status = 'ENTREGUE_PARCIAL' AND s.encerrada_em IS NULL AND s.data_entrega IS NOT NULL
            THEN s.data_entrega + INTERVAL '72 hours' END            AS expiracao_entrega_parcial_em,
       ((now() AT TIME ZONE 'America/Sao_Paulo')::date
           - (s.data_solicitacao AT TIME ZONE 'America/Sao_Paulo')::date) AS dias_desde_solicitacao,
       CASE WHEN s.data_verificacao IS NOT NULL
            THEN round((EXTRACT(EPOCH FROM (s.data_verificacao - s.data_solicitacao)) / 3600)::numeric, 1)
       END                                                           AS horas_ate_decisao,
       CASE WHEN s.data_entrega IS NOT NULL
            THEN round((EXTRACT(EPOCH FROM (s.data_entrega - s.data_solicitacao)) / 3600)::numeric, 1)
       END                                                           AS horas_ate_entrega,
       eo.nome_identificador                                         AS estoque_origem,
       COALESCE(bo.nome, beo.nome)                                   AS base_origem,
       ed.nome_identificador                                         AS estoque_destino,
       COALESCE(bd.nome, bed.nome)                                   AS base_destino,
       aa.nome_identificador                                         AS almoxarifado_atendimento,
       s.id_obra                                                     AS obra_id,
       o.numero                                                      AS obra_numero,
       s.tipo_obra_snapshot                                          AS tipo_obra,
       s.codigo_ocorrencia_snapshot                                  AS ocorrencia,
       eqs.cod_equipe                                                AS equipe_saque,
       mr.nome                                                       AS motivo_rejeicao,
       r.numero                                                      AS reserva_numero,
       s.id_requisitante                                             AS requisitante_usuario_id,
       initcap(creq.nome_busca)                                      AS requisitante,
       s.id_aprovador                                                AS aprovador_usuario_id,
       COALESCE(itens.total_itens, 0)                                AS total_itens,
       COALESCE(itens.quantidade_solicitada, 0)                      AS quantidade_solicitada,
       COALESCE(itens.quantidade_entregue, 0)                        AS quantidade_entregue
FROM public.solicitacoes_material s
LEFT JOIN public.estoques eo ON eo.id = s.id_estoque_origem
LEFT JOIN public.bases_operacionais bo ON bo.id = eo.id_base_operacional
LEFT JOIN public.equipes eqo ON eqo.id = eo.id_equipe
LEFT JOIN public.bases_operacionais beo ON beo.id = eqo.id_base_operacional
LEFT JOIN public.estoques ed ON ed.id = s.id_estoque_destino
LEFT JOIN public.bases_operacionais bd ON bd.id = ed.id_base_operacional
LEFT JOIN public.equipes eqd ON eqd.id = ed.id_equipe
LEFT JOIN public.bases_operacionais bed ON bed.id = eqd.id_base_operacional
LEFT JOIN public.estoques aa ON aa.id = s.id_almoxarifado_atendimento
LEFT JOIN public.obras o ON o.id = s.id_obra
LEFT JOIN public.equipes eqs ON eqs.id = s.id_equipe_saque
LEFT JOIN public.motivos_rejeicao mr ON mr.id = s.id_motivo_rejeicao
LEFT JOIN public.reservas_solicitacao_tecnica r ON r.id_solicitacao = s.id
LEFT JOIN public.usuarios ureq ON ureq.id = s.id_requisitante
LEFT JOIN public.colaboradores creq ON creq.id = ureq.id_colaborador
LEFT JOIN LATERAL (
    SELECT count(*)                                 AS total_itens,
           COALESCE(sum(i.quantidade), 0)          AS quantidade_solicitada,
           COALESCE(sum(i.quantidade_entregue), 0) AS quantidade_entregue
    FROM public.itens_solicitacao i
    WHERE i.id_solicitacao = s.id AND i.ativo = TRUE
) itens ON TRUE
WHERE s.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.itens_solicitacao AS
SELECT i.id                                                         AS item_id,
       i.id_solicitacao                                             AS solicitacao_id,
       s.contexto,
       s.status                                                     AS status_solicitacao,
       s.data_solicitacao,
       date_trunc('month', s.data_solicitacao AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       i.id_material                                                AS material_id,
       m.cod_material                                               AS material_codigo,
       m.descricao                                                  AS material,
       m.unidade_medida,
       i.quantidade                                                 AS quantidade_solicitada,
       COALESCE(i.quantidade_entregue, 0)                           AS quantidade_entregue,
       COALESCE(i.quantidade_devolvida_bruta, 0) - COALESCE(i.quantidade_devolucao_estornada, 0)
                                                                    AS quantidade_devolvida,
       GREATEST(i.quantidade - COALESCE(i.quantidade_entregue, 0), 0) AS quantidade_pendente,
       s.id_obra                                                    AS obra_id,
       o.numero                                                     AS obra_numero
FROM public.itens_solicitacao i
JOIN public.solicitacoes_material s ON s.id = i.id_solicitacao
JOIN public.materiais m ON m.id = i.id_material
LEFT JOIN public.obras o ON o.id = s.id_obra
WHERE i.ativo = TRUE AND s.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.reservas AS
SELECT r.id                                                          AS reserva_id,
       r.numero                                                      AS reserva_numero,
       r.id_solicitacao                                              AS solicitacao_id,
       r.data_criacao                                                AS data_reserva,
       r.registrada_em,
       r.atualizada_em,
       r.nome_agente_snapshot                                        AS agente_responsavel,
       r.empresa_agente_snapshot                                     AS empresa_agente,
       s.contexto,
       CASE s.tipo_tecnico
           WHEN '221' THEN 'ADICAO_COMUM'
           WHEN '921' THEN 'ADICAO_CONCRETO'
           WHEN '222' THEN 'DEVOLUCAO_COMUM'
           WHEN '922' THEN 'DEVOLUCAO_CONCRETO'
       END                                                           AS tipo_tecnico,
       s.status                                                      AS status_solicitacao,
       s.status_separacao,
       s.data_solicitacao,
       s.data_prevista_retirada,
       s.data_entrega,
       s.encerrada_em,
       o.numero                                                      AS obra_numero,
       s.id_obra                                                     AS obra_id,
       s.tipo_obra_snapshot                                          AS tipo_obra,
       eqs.cod_equipe                                                AS equipe_saque,
       COALESCE(bo.nome, beo.nome)                                   AS base_origem,
       (s.data_prevista_retirada - (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS dias_para_retirada,
       ((now() AT TIME ZONE 'America/Sao_Paulo')::date - r.data_criacao) AS dias_desde_reserva,
       CASE WHEN s.status = 'ENTREGUE_PARCIAL' AND s.encerrada_em IS NULL AND s.data_entrega IS NOT NULL
            THEN s.data_entrega + INTERVAL '72 hours' END            AS expiracao_entrega_parcial_em,
       CASE
           WHEN s.encerrada_em IS NOT NULL
                OR s.status IN ('ENTREGUE', 'DEVOLVIDO', 'CANCELADO', 'REJEITADO', 'ESTORNADO') THEN 'CONCLUIDA'
           WHEN s.status = 'ENTREGUE_PARCIAL' THEN 'ENTREGA_PARCIAL'
           WHEN s.data_prevista_retirada IS NULL THEN 'SEM_DATA_PREVISTA'
           WHEN s.data_prevista_retirada < (now() AT TIME ZONE 'America/Sao_Paulo')::date THEN 'ATRASADA'
           WHEN s.data_prevista_retirada <= (now() AT TIME ZONE 'America/Sao_Paulo')::date + 2 THEN 'VENCE_EM_ATE_2_DIAS'
           ELSE 'NO_PRAZO'
       END                                                           AS situacao_prazo
FROM public.reservas_solicitacao_tecnica r
JOIN public.solicitacoes_material s ON s.id = r.id_solicitacao
LEFT JOIN public.obras o ON o.id = s.id_obra
LEFT JOIN public.equipes eqs ON eqs.id = s.id_equipe_saque
LEFT JOIN public.estoques eo ON eo.id = s.id_estoque_origem
LEFT JOIN public.bases_operacionais bo ON bo.id = eo.id_base_operacional
LEFT JOIN public.equipes eqo ON eqo.id = eo.id_equipe
LEFT JOIN public.bases_operacionais beo ON beo.id = eqo.id_base_operacional
WHERE s.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.operacoes_solicitacao AS
SELECT op.id                                                         AS operacao_id,
       op.id_solicitacao                                             AS solicitacao_id,
       op.tipo                                                       AS tipo_operacao,
       CASE op.tipo_tecnico
           WHEN '221' THEN 'ADICAO_COMUM'
           WHEN '921' THEN 'ADICAO_CONCRETO'
           WHEN '222' THEN 'DEVOLUCAO_COMUM'
           WHEN '922' THEN 'DEVOLUCAO_CONCRETO'
       END                                                           AS tipo_tecnico,
       op.realizada_em,
       date_trunc('month', op.realizada_em AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       op.origem,
       o.numero                                                      AS obra_numero,
       eq.cod_equipe                                                 AS equipe,
       op.saque_fora_equipe,
       op.id_usuario_operador                                        AS operador_usuario_id,
       initcap(cop.nome_busca)                                       AS operador,
       (SELECT count(*) FROM public.itens_operacao_solicitacao io WHERE io.id_operacao = op.id) AS total_itens,
       (SELECT COALESCE(sum(io.quantidade), 0) FROM public.itens_operacao_solicitacao io WHERE io.id_operacao = op.id) AS quantidade_total
FROM public.operacoes_solicitacao_material op
LEFT JOIN public.obras o ON o.id = op.id_obra
LEFT JOIN public.equipes eq ON eq.id = op.id_equipe
LEFT JOIN public.usuarios uop ON uop.id = op.id_usuario_operador
LEFT JOIN public.colaboradores cop ON cop.id = uop.id_colaborador;

CREATE OR REPLACE VIEW copiloto.historico_solicitacoes AS
SELECT ev.id                                                         AS evento_id,
       ev.id_solicitacao                                             AS solicitacao_id,
       ev.sequencia,
       ev.tipo_evento,
       ev.origem,
       ev.ocorrido_em,
       date_trunc('month', ev.ocorrido_em AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       ev.id_autor                                                   AS autor_usuario_id,
       ev.autor_snapshot                                             AS autor
FROM public.eventos_solicitacao_material ev;

-- =============================================================================================
-- OBRAS E PROJETOS
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.obras AS
SELECT o.id                                           AS obra_id,
       o.numero                                       AS obra_numero,
       o.titulo,
       o.descricao,
       o.tipo_obra,
       so.nome                                        AS status,
       COALESCE(so.concluida, FALSE)                  AS status_concluido,
       o.valor_projeto,
       o.data_inicio,
       o.data_previsao_fim,
       o.data_fim_efetiva,
       (o.data_fim_efetiva IS NULL
           AND COALESCE(so.concluida, FALSE) = FALSE
           AND o.data_previsao_fim < (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS atrasada,
       (o.data_previsao_fim - (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS dias_para_previsao_fim,
       b.nome                                         AS base_operacional,
       mu.nome                                        AS municipio,
       pn.nome                                        AS processo,
       ag.nome                                        AS agente_responsavel,
       ag.empresa_orgao                               AS empresa_agente,
       initcap(csup.nome_busca)                       AS supervisor,
       initcap(ccoo.nome_busca)                       AS coordenador,
       op.numero                                      AS obra_pai_numero,
       o.tipo_vinculo_pai,
       o.ativo,
       o.data_criacao                                 AS criada_em
FROM public.obras o
LEFT JOIN public.status_obras so ON so.id = o.id_status_obra
LEFT JOIN public.bases_operacionais b ON b.id = o.id_base_operacional
LEFT JOIN public.municipios mu ON mu.id = o.id_municipio
LEFT JOIN public.processos_negocio pn ON pn.id = o.id_processo
LEFT JOIN public.agentes_responsaveis_obras ag ON ag.id = o.id_agente_responsavel
LEFT JOIN public.supervisores sup ON sup.id = o.id_supervisor
LEFT JOIN public.colaboradores csup ON csup.id = sup.id_colaborador
LEFT JOIN public.coordenadores coo ON coo.id = o.id_coordenador
LEFT JOIN public.colaboradores ccoo ON ccoo.id = coo.id_colaborador
LEFT JOIN public.obras op ON op.id = o.id_obra_pai;

CREATE OR REPLACE VIEW copiloto.obras_relacionamentos AS
SELECT rel.id            AS relacionamento_id,
       rel.tipo,
       oa.numero         AS obra_numero,
       oa.id             AS obra_id,
       ob.numero         AS obra_relacionada_numero,
       ob.id             AS obra_relacionada_id,
       rel.data_criacao  AS criado_em
FROM public.obras_relacionamentos rel
JOIN public.obras oa ON oa.id = rel.id_projeto_a
JOIN public.obras ob ON ob.id = rel.id_projeto_b
WHERE rel.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.obras_equipes AS
SELECT o.id           AS obra_id,
       o.numero       AS obra_numero,
       eq.id          AS equipe_id,
       eq.cod_equipe  AS equipe
FROM public.obras_equipes oe
JOIN public.obras o ON o.id = oe.id_obra
JOIN public.equipes eq ON eq.id = oe.id_equipe;

CREATE OR REPLACE VIEW copiloto.obras_anexos AS
SELECT an.id             AS anexo_id,
       o.id              AS obra_id,
       o.numero          AS obra_numero,
       ta.nome           AS tipo_anexo,
       an.nome_arquivo,
       an.content_type   AS tipo_conteudo,
       an.tamanho_bytes,
       an.data_criacao   AS enviado_em
FROM public.anexos_obras an
JOIN public.obras o ON o.id = an.id_obra
LEFT JOIN public.tipos_anexos_obras ta ON ta.id = an.id_tipo_anexo
WHERE an.ativo = TRUE;

-- =============================================================================================
-- RH E ESTRUTURA OPERACIONAL
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.bases_operacionais AS
SELECT b.id    AS base_operacional_id,
       b.nome  AS base_operacional,
       b.uf,
       cf.nome AS centro_faturamento,
       b.ativo
FROM public.bases_operacionais b
LEFT JOIN public.centros_faturamento cf ON cf.id = b.id_centro_custo;

CREATE OR REPLACE VIEW copiloto.equipes AS
SELECT eq.id              AS equipe_id,
       eq.cod_equipe      AS equipe,
       te.nome            AS tipo_equipe,
       b.nome             AS base_operacional,
       pn.nome            AS processo,
       c.numero_contrato  AS contrato,
       v.placa            AS veiculo_placa,
       initcap(csup.nome_busca) AS supervisor,
       initcap(ccoo.nome_busca) AS coordenador,
       (SELECT count(*)
          FROM public.equipe_membros em
         WHERE em.id_equipe = eq.id AND em.ativo = TRUE AND em.data_saida IS NULL) AS membros_ativos,
       eq.ativo
FROM public.equipes eq
LEFT JOIN public.tipos_equipe te ON te.id = eq.id_tipo_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
LEFT JOIN public.processos_negocio pn ON pn.id = eq.id_processo
LEFT JOIN public.contratos c ON c.id = eq.id_contrato
LEFT JOIN public.veiculos v ON v.id = eq.id_veiculo_vinculado
LEFT JOIN public.supervisores sup ON sup.id = eq.id_supervisor
LEFT JOIN public.colaboradores csup ON csup.id = sup.id_colaborador
LEFT JOIN public.coordenadores coo ON coo.id = eq.id_coordenador
LEFT JOIN public.colaboradores ccoo ON ccoo.id = coo.id_colaborador;

-- Nome exibido a partir do campo de busca normalizado (sem acentos). CPF e nome cifrado nunca saem.
CREATE OR REPLACE VIEW copiloto.colaboradores AS
SELECT c.id                    AS colaborador_id,
       initcap(c.nome_busca)   AS nome,
       f.nome                  AS funcao,
       b.nome                  AS base_operacional,
       pn.nome                 AS processo,
       c.data_contratacao,
       (SELECT eq.cod_equipe
          FROM public.equipe_membros em
          JOIN public.equipes eq ON eq.id = em.id_equipe
         WHERE em.id_colaborador = c.id AND em.ativo = TRUE AND em.data_saida IS NULL
         ORDER BY em.data_entrada DESC
         LIMIT 1)              AS equipe_atual,
       c.id_usuario            AS usuario_id,
       c.ativo
FROM public.colaboradores c
LEFT JOIN public.funcoes f ON f.id = c.id_funcao
LEFT JOIN public.bases_operacionais b ON b.id = c.id_base_operacional
LEFT JOIN public.processos_negocio pn ON pn.id = c.id_processo;

CREATE OR REPLACE VIEW copiloto.equipe_membros AS
SELECT em.id                   AS membro_id,
       eq.id                   AS equipe_id,
       eq.cod_equipe           AS equipe,
       c.id                    AS colaborador_id,
       initcap(c.nome_busca)   AS colaborador,
       f.nome                  AS funcao_colaborador,
       em.funcao               AS papel_na_equipe,
       em.data_entrada,
       em.data_saida,
       (em.ativo = TRUE AND em.data_saida IS NULL) AS vinculo_ativo
FROM public.equipe_membros em
JOIN public.equipes eq ON eq.id = em.id_equipe
JOIN public.colaboradores c ON c.id = em.id_colaborador
LEFT JOIN public.funcoes f ON f.id = c.id_funcao;

CREATE OR REPLACE VIEW copiloto.supervisores AS
SELECT s.id                    AS supervisor_id,
       initcap(c.nome_busca)   AS supervisor,
       initcap(cc.nome_busca)  AS coordenador,
       b.nome                  AS base_operacional,
       s.data_inicio,
       s.data_fim,
       s.ativo
FROM public.supervisores s
JOIN public.colaboradores c ON c.id = s.id_colaborador
LEFT JOIN public.coordenadores co ON co.id = s.id_coordenador
LEFT JOIN public.colaboradores cc ON cc.id = co.id_colaborador
LEFT JOIN public.bases_operacionais b ON b.id = c.id_base_operacional;

CREATE OR REPLACE VIEW copiloto.coordenadores AS
SELECT co.id                   AS coordenador_id,
       initcap(c.nome_busca)   AS coordenador,
       b.nome                  AS base_operacional,
       co.data_inicio,
       co.data_fim,
       co.ativo
FROM public.coordenadores co
JOIN public.colaboradores c ON c.id = co.id_colaborador
LEFT JOIN public.bases_operacionais b ON b.id = c.id_base_operacional;

CREATE OR REPLACE VIEW copiloto.veiculos AS
SELECT v.id          AS veiculo_id,
       v.placa,
       v.modelo,
       v.fabricante,
       b.nome        AS base_operacional,
       (SELECT eq.cod_equipe FROM public.equipes eq
         WHERE eq.id_veiculo_vinculado = v.id AND eq.ativo = TRUE LIMIT 1) AS equipe_vinculada,
       v.ativo
FROM public.veiculos v
LEFT JOIN public.bases_operacionais b ON b.id = v.id_base_operacional;

CREATE OR REPLACE VIEW copiloto.municipios AS
SELECT mu.id    AS municipio_id,
       mu.nome  AS municipio,
       b.nome   AS base_operacional,
       mu.ativo
FROM public.municipios mu
LEFT JOIN public.bases_operacionais b ON b.id = mu.id_base_operacional;

CREATE OR REPLACE VIEW copiloto.funcoes AS
SELECT f.id AS funcao_id, f.nome AS funcao, f.descricao, f.ativo
FROM public.funcoes f;

CREATE OR REPLACE VIEW copiloto.processos_negocio AS
SELECT pn.id AS processo_id, pn.nome AS processo, pn.ativo
FROM public.processos_negocio pn;

-- =============================================================================================
-- EXECUCAO EM CAMPO (TURNOS E EXECUCOES COMERCIAIS)
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.turnos AS
SELECT t.id                                                     AS turno_id,
       eq.cod_equipe                                            AS equipe,
       b.nome                                                   AS base_operacional,
       t.status,
       t.data_inicio,
       t.data_fim,
       (t.data_inicio AT TIME ZONE 'America/Sao_Paulo')::date   AS data,
       date_trunc('month', t.data_inicio AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       CASE WHEN t.data_fim IS NOT NULL
            THEN round((EXTRACT(EPOCH FROM (t.data_fim - t.data_inicio)) / 3600)::numeric, 2) END AS duracao_horas,
       initcap(cab.nome_busca)                                  AS aberto_por,
       (SELECT count(*) FROM public.execucoes_comercial ex WHERE ex.id_turno = t.id AND ex.ativo = TRUE) AS total_execucoes
FROM public.turnos t
LEFT JOIN public.equipes eq ON eq.id = t.id_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
LEFT JOIN public.usuarios uab ON uab.id = t.id_usuario_abertura
LEFT JOIN public.colaboradores cab ON cab.id = uab.id_colaborador
WHERE t.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.execucoes_comerciais AS
SELECT ex.id                                                   AS execucao_id,
       ex.status_execucao,
       ex.data_inicio,
       ex.data_fim,
       (ex.data_inicio AT TIME ZONE 'America/Sao_Paulo')::date AS data,
       date_trunc('month', ex.data_inicio AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       CASE WHEN ex.data_fim IS NOT NULL
            THEN round((EXTRACT(EPOCH FROM (ex.data_fim - ex.data_inicio)) / 60)::numeric, 1) END AS duracao_minutos,
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

CREATE OR REPLACE VIEW copiloto.execucoes_acoes AS
SELECT ea.id                                                   AS execucao_acao_id,
       ex.id                                                   AS execucao_id,
       ex.data_inicio,
       date_trunc('month', ex.data_inicio AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       eq.cod_equipe                                           AS equipe,
       a.cod_acao                                              AS acao_codigo,
       a.nome                                                  AS acao,
       ex.status_execucao
FROM public.execucoes_acoes ea
JOIN public.execucoes_comercial ex ON ex.id = ea.id_execucao
JOIN public.acoes a ON a.id = ea.id_acao
LEFT JOIN public.equipes eq ON eq.id = ex.id_equipe
WHERE ea.ativo = TRUE AND ex.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.execucoes_materiais AS
SELECT em.id                                                   AS execucao_material_id,
       ex.id                                                   AS execucao_id,
       ex.data_inicio,
       date_trunc('month', ex.data_inicio AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
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

-- =============================================================================================
-- CONTRATOS
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.contratos AS
SELECT c.id                     AS contrato_id,
       c.numero_contrato        AS contrato,
       c.cliente,
       c.data_vigencia,
       c.data_encerramento,
       c.coeficiente_faturamento,
       (c.ativo = TRUE AND (c.data_encerramento IS NULL
            OR c.data_encerramento >= (now() AT TIME ZONE 'America/Sao_Paulo')::date)) AS vigente,
       (SELECT count(*) FROM public.equipes eq WHERE eq.id_contrato = c.id AND eq.ativo = TRUE) AS equipes_ativas,
       c.ativo
FROM public.contratos c;

CREATE OR REPLACE VIEW copiloto.acoes AS
SELECT a.id AS acao_id, a.cod_acao AS acao_codigo, a.nome AS acao, a.ativo
FROM public.acoes a;

CREATE OR REPLACE VIEW copiloto.acoes_valores AS
SELECT av.id               AS acao_valor_id,
       a.cod_acao          AS acao_codigo,
       a.nome              AS acao,
       c.numero_contrato   AS contrato,
       av.valor_base,
       av.data_vigencia,
       av.ativo
FROM public.acoes_valores av
JOIN public.acoes a ON a.id = av.id_acao
LEFT JOIN public.contratos c ON c.id = av.id_contrato;

-- =============================================================================================
-- DESCONTOS (DEPARTAMENTO PESSOAL)
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.descontos AS
SELECT d.id                                                      AS desconto_id,
       d.status,
       d.data_ocorrencia,
       (d.data_ocorrencia AT TIME ZONE 'America/Sao_Paulo')::date AS data,
       date_trunc('month', d.data_ocorrencia AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       d.colaborador_id,
       initcap(c.nome_busca)                                     AS colaborador,
       f.nome                                                    AS funcao_colaborador,
       b.nome                                                    AS base_operacional,
       md.descricao                                              AS motivo,
       d.quantidade_parcelas,
       d.valor_sugerido,
       d.valor_validado,
       d.valor_baixado,
       COALESCE(d.valor_baixado, d.valor_validado, d.valor_sugerido) AS valor_referencia,
       d.data_criacao                                            AS solicitado_em,
       d.data_validacao,
       d.data_baixa,
       d.data_rejeicao,
       CASE WHEN d.data_validacao IS NOT NULL
            THEN round((EXTRACT(EPOCH FROM (d.data_validacao - d.data_criacao)) / 86400)::numeric, 1) END AS dias_ate_validacao,
       d.usuario_solicitante_id                                  AS solicitante_usuario_id,
       initcap(csol.nome_busca)                                  AS solicitante,
       (SELECT count(*) FROM public.desconto_item di WHERE di.desconto_id = d.id AND di.ativo = TRUE) AS total_itens,
       (SELECT COALESCE(sum(di.valor), 0) FROM public.desconto_item di WHERE di.desconto_id = d.id AND di.ativo = TRUE) AS valor_itens,
       EXISTS (SELECT 1 FROM public.desconto_anexo da WHERE da.desconto_id = d.id AND da.ativo = TRUE) AS possui_anexo
FROM public.desconto d
LEFT JOIN public.colaboradores c ON c.id = d.colaborador_id
LEFT JOIN public.funcoes f ON f.id = c.id_funcao
LEFT JOIN public.bases_operacionais b ON b.id = c.id_base_operacional
LEFT JOIN public.motivo_desconto md ON md.id = d.motivo_desconto_id
LEFT JOIN public.usuarios usol ON usol.id = d.usuario_solicitante_id
LEFT JOIN public.colaboradores csol ON csol.id = usol.id_colaborador
WHERE d.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.descontos_itens AS
SELECT di.id                                                     AS item_id,
       d.id                                                      AS desconto_id,
       d.status                                                  AS status_desconto,
       d.data_ocorrencia,
       date_trunc('month', d.data_ocorrencia AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       initcap(c.nome_busca)                                     AS colaborador,
       md.descricao                                              AS motivo,
       te.descricao                                              AS tipo_equipamento,
       di.descricao_material                                     AS material,
       di.valor
FROM public.desconto_item di
JOIN public.desconto d ON d.id = di.desconto_id
LEFT JOIN public.colaboradores c ON c.id = d.colaborador_id
LEFT JOIN public.motivo_desconto md ON md.id = d.motivo_desconto_id
LEFT JOIN public.tipo_equipamento te ON te.id = di.tipo_equipamento_id
WHERE di.ativo = TRUE AND d.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.motivos_desconto AS
SELECT md.id AS motivo_id, md.descricao AS motivo, md.teto_parcelas, md.ativo
FROM public.motivo_desconto md;

-- =============================================================================================
-- FINANCEIRO (SOLICITACOES DE COMPRA, PAGAMENTO, DOCUMENTOS E FORNECEDORES)
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.solicitacoes_compra AS
SELECT sc.id                                                     AS solicitacao_id,
       sc.numero,
       fs.titulo                                                 AS formulario,
       sc.status,
       sc.prioridade,
       sc.tipo_despesa,
       sc.forma_pagamento,
       sc.local_aquisicao,
       sc.descricao,
       b.nome                                                    AS base_operacional,
       pn.nome                                                   AS processo,
       o.numero                                                  AS obra_numero,
       COALESCE(fo.nome_fantasia, sc.fornecedor_nome)            AS fornecedor,
       COALESCE(fo.razao_social, sc.fornecedor_razao_social)     AS fornecedor_razao_social,
       sc.valor,
       sc.valor_aprovado,
       sc.quantidade,
       sc.quantidade_parcelas,
       sc.vencimento,
       (sc.vencimento - (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS dias_para_vencimento,
       sc.tem_nf                                                 AS possui_nota_fiscal,
       v.placa                                                   AS veiculo_placa,
       sc.regime_veiculo,
       sc.natureza_servico,
       sc.motivo_servico,
       initcap(csol.nome_busca)                                  AS solicitante,
       sc.data_criacao                                           AS criada_em,
       sc.data_submissao,
       date_trunc('month', COALESCE(sc.data_submissao, sc.data_criacao) AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       sc.data_decisao,
       CASE WHEN sc.data_decisao IS NOT NULL AND sc.data_submissao IS NOT NULL
            THEN round((EXTRACT(EPOCH FROM (sc.data_decisao - sc.data_submissao)) / 3600)::numeric, 1) END AS horas_ate_decisao,
       sc.data_cancelamento
FROM public.financeiro_solicitacao_compra sc
LEFT JOIN public.financeiro_formulario_solicitacao fs ON fs.id = sc.formulario_id
LEFT JOIN public.bases_operacionais b ON b.id = sc.base_id
LEFT JOIN public.processos_negocio pn ON pn.id = sc.processo_id
LEFT JOIN public.obras o ON o.id = sc.obra_id
LEFT JOIN public.financeiro_fornecedor fo ON fo.id = sc.fornecedor_id
LEFT JOIN public.veiculos v ON v.id = sc.veiculo_id
LEFT JOIN public.colaboradores csol ON csol.id = sc.solicitante_colaborador_id
WHERE sc.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.solicitacoes_pagamento AS
SELECT sp.id                                                     AS solicitacao_id,
       sp.numero,
       fs.titulo                                                 AS formulario,
       sp.status,
       sp.prioridade,
       sp.tipo_despesa,
       sp.forma_pagamento,
       sp.local_aquisicao,
       sp.descricao,
       b.nome                                                    AS base_operacional,
       pn.nome                                                   AS processo,
       o.numero                                                  AS obra_numero,
       COALESCE(fo.nome_fantasia, sp.fornecedor_nome)            AS fornecedor,
       COALESCE(fo.razao_social, sp.fornecedor_razao_social)     AS fornecedor_razao_social,
       sp.valor,
       sp.valor_aprovado,
       sp.quantidade,
       sp.quantidade_parcelas,
       sp.vencimento,
       (sp.vencimento - (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS dias_para_vencimento,
       sp.tem_nf                                                 AS possui_nota_fiscal,
       v.placa                                                   AS veiculo_placa,
       sp.regime_veiculo,
       sp.natureza_servico,
       sp.motivo_servico,
       sp.possui_compra_previa,
       scp.numero                                                AS solicitacao_compra_numero,
       sp.motivo_sem_compra,
       initcap(csol.nome_busca)                                  AS solicitante,
       sp.data_criacao                                           AS criada_em,
       sp.data_submissao,
       date_trunc('month', COALESCE(sp.data_submissao, sp.data_criacao) AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       sp.data_decisao,
       CASE WHEN sp.data_decisao IS NOT NULL AND sp.data_submissao IS NOT NULL
            THEN round((EXTRACT(EPOCH FROM (sp.data_decisao - sp.data_submissao)) / 3600)::numeric, 1) END AS horas_ate_decisao,
       sp.data_cancelamento
FROM public.financeiro_solicitacao_pagamento sp
LEFT JOIN public.financeiro_formulario_solicitacao fs ON fs.id = sp.formulario_id
LEFT JOIN public.bases_operacionais b ON b.id = sp.base_id
LEFT JOIN public.processos_negocio pn ON pn.id = sp.processo_id
LEFT JOIN public.obras o ON o.id = sp.obra_id
LEFT JOIN public.financeiro_fornecedor fo ON fo.id = sp.fornecedor_id
LEFT JOIN public.veiculos v ON v.id = sp.veiculo_id
LEFT JOIN public.financeiro_solicitacao_compra scp ON scp.id = sp.solicitacao_compra_id
LEFT JOIN public.colaboradores csol ON csol.id = sp.solicitante_colaborador_id
WHERE sp.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.documentos_financeiros AS
SELECT df.id                                                     AS documento_id,
       df.tipo,
       df.situacao,
       (df.situacao IN ('PENDENTE', 'COM_PENDENCIA'))            AS em_aberto,
       df.numero_documento,
       COALESCE(fo.nome_fantasia, df.emissor_nome)               AS emissor,
       df.data_emissao,
       df.data_vencimento,
       (df.data_vencimento - (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS dias_para_vencimento,
       df.competencia,
       df.valor,
       b.nome                                                    AS base_operacional,
       pn.nome                                                   AS processo,
       o.numero                                                  AS obra_numero,
       df.origem_tipo,
       df.origem_numero,
       df.data_criacao                                           AS enviado_em,
       df.data_conferencia
FROM public.financeiro_documento df
LEFT JOIN public.financeiro_fornecedor fo ON fo.id = df.fornecedor_id
LEFT JOIN public.bases_operacionais b ON b.id = df.base_id
LEFT JOIN public.processos_negocio pn ON pn.id = df.processo_id
LEFT JOIN public.obras o ON o.id = df.obra_id
WHERE df.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.fornecedores AS
SELECT fo.id              AS fornecedor_id,
       fo.nome_fantasia   AS fornecedor,
       fo.razao_social,
       fo.status,
       fo.ativo,
       (SELECT count(*) FROM public.financeiro_solicitacao_compra sc WHERE sc.fornecedor_id = fo.id AND sc.ativo = TRUE) AS total_solicitacoes_compra,
       (SELECT count(*) FROM public.financeiro_solicitacao_pagamento sp WHERE sp.fornecedor_id = fo.id AND sp.ativo = TRUE) AS total_solicitacoes_pagamento
FROM public.financeiro_fornecedor fo;

-- =============================================================================================
-- SEGURANCA DO TRABALHO (SST)
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.inspecoes AS
SELECT i.id                                                      AS inspecao_id,
       i.data_realizacao,
       (i.data_realizacao AT TIME ZONE 'America/Sao_Paulo')::date AS data,
       date_trunc('month', i.data_realizacao AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       i.status,
       ti.nome                                                   AS tipo_inspecao,
       fi.titulo                                                 AS formulario,
       pn.nome                                                   AS processo,
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
LEFT JOIN public.colaboradores cin ON cin.id = uin.id_colaborador
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
       b.nome                                                    AS base_operacional,
       eq.cod_equipe                                             AS equipe,
       initcap(cin.nome_busca)                                   AS inspetor,
       (SELECT count(*) FROM public.desvio_colaboradores dc WHERE dc.id_desvio = d.id AND dc.ativo = TRUE) AS total_colaboradores_envolvidos
FROM public.desvios_seguranca d
JOIN public.inspecoes_seguranca i ON i.id = d.id_inspecao
LEFT JOIN public.perguntas_inspecao pq ON pq.id = d.id_pergunta
LEFT JOIN public.tipos_inspecao ti ON ti.id = i.id_tipo_inspecao
LEFT JOIN public.bases_operacionais b ON b.id = i.id_base_operacional
LEFT JOIN public.equipes eq ON eq.id = i.id_equipe
LEFT JOIN public.usuarios uin ON uin.id = i.id_inspetor
LEFT JOIN public.colaboradores cin ON cin.id = uin.id_colaborador
WHERE d.ativo = TRUE AND i.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.eventos_sst AS
SELECT e.id                                                      AS evento_id,
       e.numero_registro,
       e.tipo,
       e.status,
       e.data_ocorrencia,
       (e.data_ocorrencia AT TIME ZONE 'America/Sao_Paulo')::date AS data,
       date_trunc('month', e.data_ocorrencia AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       e.data_comunicacao,
       e.atividade_nome                                          AS atividade,
       e.base_nome                                               AS base_operacional,
       e.equipe_codigo                                           AS equipe,
       e.veiculo_placa,
       cls.nome                                                  AS classificacao,
       ll.nome                                                   AS local_lesao,
       nl.nome                                                   AS natureza_lesao,
       ac.nome                                                   AS agente_causador,
       e.dias_perdidos,
       e.possui_cat,
       e.tipo_cat,
       e.data_emissao_cat,
       e.data_conclusao,
       e.data_cancelamento,
       (SELECT count(*) FROM public.evento_sst_envolvidos en
         WHERE en.id_evento = e.id AND en.ativo = TRUE AND en.papel = 'ACIDENTADO') AS total_acidentados,
       (SELECT count(*) FROM public.evento_sst_envolvidos en
         WHERE en.id_evento = e.id AND en.ativo = TRUE) AS total_envolvidos
FROM public.eventos_sst e
LEFT JOIN public.catalogos_sst cls ON cls.id = e.id_classificacao
LEFT JOIN public.catalogos_sst ll ON ll.id = e.id_local_lesao
LEFT JOIN public.catalogos_sst nl ON nl.id = e.id_natureza_lesao
LEFT JOIN public.catalogos_sst ac ON ac.id = e.id_agente_causador
WHERE e.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.metas_inspecao AS
SELECT m.id                    AS meta_id,
       m.escopo,
       f.nome                  AS funcao,
       initcap(c.nome_busca)   AS colaborador,
       ti.nome                 AS tipo_inspecao,
       m.quantidade,
       m.periodicidade,
       m.vigencia_inicio,
       m.vigencia_fim,
       m.descricao,
       (m.vigencia_inicio <= (now() AT TIME ZONE 'America/Sao_Paulo')::date
           AND (m.vigencia_fim IS NULL OR m.vigencia_fim >= (now() AT TIME ZONE 'America/Sao_Paulo')::date)) AS vigente
FROM public.metas_inspecao m
LEFT JOIN public.funcoes f ON f.id = m.id_funcao
LEFT JOIN public.colaboradores c ON c.id = m.id_colaborador
LEFT JOIN public.tipos_inspecao ti ON ti.id = m.id_tipo_inspecao
WHERE m.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.corpos_governanca AS
SELECT cg.id                    AS corpo_id,
       cg.tipo,
       cg.regime_composicao,
       cg.status,
       cg.nome,
       COALESCE(b.nome, cg.base_nome) AS base_operacional,
       cg.uf,
       cg.inicio_periodo,
       cg.fim_periodo,
       cg.grau_risco,
       cg.data_ativacao,
       cg.data_encerramento,
       (SELECT count(*) FROM public.membros_corpo_sst mc
         WHERE mc.id_corpo = cg.id AND mc.ativo = TRUE AND mc.fim_participacao IS NULL) AS membros_ativos
FROM public.corpos_governanca_sst cg
LEFT JOIN public.bases_operacionais b ON b.id = cg.id_base_operacional
WHERE cg.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.membros_governanca AS
SELECT mc.id                    AS membro_id,
       cg.id                    AS corpo_id,
       cg.tipo                  AS tipo_corpo,
       cg.nome                  AS corpo,
       initcap(c.nome_busca)    AS colaborador,
       mc.papel,
       mc.representacao,
       mc.titularidade,
       mc.origem,
       mc.inicio_participacao,
       mc.fim_participacao,
       mc.vacancia_pendente,
       (mc.fim_participacao IS NULL) AS ativo_no_corpo
FROM public.membros_corpo_sst mc
JOIN public.corpos_governanca_sst cg ON cg.id = mc.id_corpo
LEFT JOIN public.colaboradores c ON c.id = mc.id_colaborador
WHERE mc.ativo = TRUE AND cg.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.reunioes_governanca AS
SELECT r.id                                                      AS reuniao_id,
       r.tipo_corpo,
       r.tipo,
       r.status,
       r.data_hora,
       date_trunc('month', r.data_hora AT TIME ZONE 'America/Sao_Paulo')::date AS mes,
       cg.nome                                                   AS corpo,
       COALESCE(b.nome, cg.base_nome)                            AS base_operacional,
       r.local_ou_meio,
       r.duracao_minutos,
       r.iniciada_em,
       r.finalizada_em,
       r.cancelada_em,
       initcap(cres.nome_busca)                                  AS responsavel,
       (SELECT count(*) FROM public.participacoes_reuniao_sst p WHERE p.id_reuniao = r.id AND p.ativo = TRUE) AS total_participantes
FROM public.reunioes_governanca_sst r
LEFT JOIN public.corpos_governanca_sst cg ON cg.id = r.id_corpo
LEFT JOIN public.bases_operacionais b ON b.id = r.id_base_operacional
LEFT JOIN public.usuarios ures ON ures.id = r.id_responsavel
LEFT JOIN public.colaboradores cres ON cres.id = ures.id_colaborador
WHERE r.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.acoes_governanca AS
SELECT a.id                                                      AS acao_id,
       a.titulo,
       a.tipo,
       a.prioridade,
       a.status,
       a.prazo,
       CASE
           WHEN a.status IN ('CONCLUIDA', 'CANCELADA') THEN 'ENCERRADA'
           WHEN a.prazo IS NULL THEN 'SEM_PRAZO'
           WHEN a.prazo < (now() AT TIME ZONE 'America/Sao_Paulo')::date THEN 'VENCIDA'
           WHEN a.prazo = (now() AT TIME ZONE 'America/Sao_Paulo')::date THEN 'VENCE_HOJE'
           ELSE 'NO_PRAZO'
       END                                                       AS situacao_prazo,
       (a.prazo - (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS dias_para_prazo,
       cg.tipo                                                   AS tipo_corpo,
       cg.nome                                                   AS corpo,
       COALESCE(b.nome, cg.base_nome)                            AS base_operacional,
       initcap(cres.nome_busca)                                  AS responsavel,
       a.data_criacao                                            AS criada_em,
       a.data_inicio,
       a.data_conclusao,
       a.data_cancelamento
FROM public.acoes_governanca_sst a
LEFT JOIN public.corpos_governanca_sst cg ON cg.id = a.id_corpo
LEFT JOIN public.bases_operacionais b ON b.id = a.id_base_operacional
LEFT JOIN public.usuarios ures ON ures.id = a.id_responsavel
LEFT JOIN public.colaboradores cres ON cres.id = ures.id_colaborador
WHERE a.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.documentos_sst AS
SELECT dg.id                                                     AS documento_id,
       cat.nome                                                  AS categoria,
       dg.categoria_codigo,
       dg.contexto,
       dg.escopo_titular,
       b.nome                                                    AS base_operacional,
       initcap(c.nome_busca)                                     AS colaborador,
       dg.numero_documento,
       dg.emissor,
       dg.uf,
       dg.data_emissao,
       dg.data_validade,
       CASE
           WHEN dg.data_validade IS NULL THEN 'SEM_VALIDADE'
           WHEN dg.data_validade < (now() AT TIME ZONE 'America/Sao_Paulo')::date THEN 'VENCIDO'
           WHEN dg.data_validade <= (now() AT TIME ZONE 'America/Sao_Paulo')::date + COALESCE(dg.antecedencia_alerta_dias, 30) THEN 'A_VENCER'
           ELSE 'REGULAR'
       END                                                       AS situacao_validade,
       (dg.data_validade - (now() AT TIME ZONE 'America/Sao_Paulo')::date) AS dias_para_vencer,
       dg.situacao_conferencia,
       dg.modalidade,
       dg.carga_horaria_minutos,
       dg.data_conclusao_treinamento,
       dg.atual,
       dg.data_criacao                                           AS enviado_em
FROM public.sst_documentos_governanca dg
LEFT JOIN public.sst_categorias_documento cat ON cat.id = dg.id_categoria
LEFT JOIN public.bases_operacionais b ON b.id = dg.id_base
LEFT JOIN public.colaboradores c ON c.id = dg.id_colaborador
WHERE dg.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.treinamentos_sst AS
SELECT t.id AS treinamento_id, t.codigo, t.nome AS treinamento, t.descricao, t.aplica_cipa, t.aplica_brigada, t.ativo
FROM public.sst_treinamentos_governanca t;

-- =============================================================================================
-- CHAMADOS (SUPORTE) - escopo: quem gerencia ve todos; os demais veem somente os proprios.
-- SLA replica Chamado.isSlaPrimeiraRespostaVencido / isSlaResolucaoVencido.
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.chamados AS
SELECT ch.id                                                   AS chamado_id,
       ch.protocolo,
       t.nome                                                  AS topico,
       ch.prioridade,
       ch.status,
       ch.assunto,
       ea.nome                                                 AS equipe_atendimento,
       ch.data_abertura,
       date_trunc('month', ch.data_abertura AT TIME ZONE 'America/Sao_Paulo')::date AS mes_abertura,
       ch.data_primeira_resposta,
       ch.data_resolucao,
       ch.data_fechamento,
       ch.data_limite_primeira_resposta,
       ch.data_limite_resolucao,
       (ch.data_inicio_pausa_sla IS NOT NULL)                  AS sla_pausado,
       (COALESCE(ch.data_primeira_resposta,
                 CASE WHEN ch.status IN ('FECHADO', 'CANCELADO') THEN ch.data_fechamento END,
                 now()) > ch.data_limite_primeira_resposta)    AS sla_primeira_resposta_vencido,
       (COALESCE(ch.data_resolucao,
                 CASE WHEN ch.status IN ('FECHADO', 'CANCELADO') THEN ch.data_fechamento END,
                 ch.data_inicio_pausa_sla,
                 now()) > ch.data_limite_resolucao)            AS sla_resolucao_vencido,
       CASE WHEN ch.status NOT IN ('RESOLVIDO', 'FECHADO', 'CANCELADO') AND ch.data_limite_resolucao IS NOT NULL
            THEN round((EXTRACT(EPOCH FROM (ch.data_limite_resolucao - now())) / 3600)::numeric, 1)
       END                                                     AS horas_para_vencer_resolucao,
       CASE WHEN ch.data_resolucao IS NOT NULL
            THEN round((EXTRACT(EPOCH FROM (ch.data_resolucao - ch.data_abertura)) / 3600)::numeric, 1)
       END                                                     AS horas_ate_resolucao,
       (ch.usuario_solicitante_id = nullif(current_setting('copiloto.usuario_id', true), '')::uuid) AS aberto_por_mim
FROM public.chamados ch
LEFT JOIN public.topicos_chamado t ON t.id = ch.id_topico
LEFT JOIN public.equipes_atendimento_chamado ea ON ea.id = ch.id_equipe_atendimento
WHERE ch.ativo = TRUE
  AND (current_setting('copiloto.escopo_chamados', true) = 'TODOS'
       OR ch.usuario_solicitante_id = nullif(current_setting('copiloto.usuario_id', true), '')::uuid);

-- =============================================================================================
-- COMUNICACAO E NOTIFICACOES
-- =============================================================================================

-- Mesmo conteudo exibido no mural a qualquer usuario autenticado.
CREATE OR REPLACE VIEW copiloto.comunicados AS
SELECT c.id             AS comunicado_id,
       c.titulo,
       c.resumo,
       c.tipo,
       c.apresentacao,
       c.status,
       c.publicado_em,
       c.data_inicio,
       c.data_fim,
       c.fixado,
       c.exige_confirmacao,
       c.prioridade_notificacao
FROM public.comunicados c
WHERE c.ativo = TRUE AND c.status = 'PUBLICADO';

-- Somente as notificacoes do proprio usuario.
CREATE OR REPLACE VIEW copiloto.minhas_notificacoes AS
SELECT n.id              AS notificacao_id,
       n.origem,
       n.categoria,
       n.prioridade,
       n.titulo,
       n.corpo,
       n.data_criacao    AS recebida_em,
       n.lida_em,
       (n.lida_em IS NULL) AS nao_lida,
       n.arquivada_em
FROM public.notificacoes n
WHERE n.ativo = TRUE
  AND n.id_usuario = nullif(current_setting('copiloto.usuario_id', true), '')::uuid;

-- =============================================================================================
-- FROTA (TELEMETRIA)
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.telemetria_eventos AS
SELECT te.id                                                     AS evento_id,
       v.placa                                                   AS veiculo_placa,
       b.nome                                                    AS base_operacional,
       te.tipo_operacao,
       te.nome_evento,
       te.data_hora,
       (te.data_hora AT TIME ZONE 'America/Sao_Paulo')::date     AS data,
       date_trunc('month', te.data_hora AT TIME ZONE 'America/Sao_Paulo')::date AS mes
FROM public.telemetria_eventos te
LEFT JOIN public.veiculos v ON v.id = te.id_veiculo_ces
LEFT JOIN public.bases_operacionais b ON b.id = v.id_base_operacional
WHERE te.ativo = TRUE;

-- =============================================================================================
-- IAM E AUDITORIA (sem login, e-mail, telefone ou snapshots de dados)
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.usuarios AS
SELECT u.id                    AS usuario_id,
       initcap(c.nome_busca)   AS colaborador,
       p.nome                  AS perfil,
       u.tipo_usuario,
       u.ativo,
       u.data_criacao          AS criado_em
FROM public.usuarios u
LEFT JOIN public.perfis_acesso p ON p.id = u.id_perfil
LEFT JOIN public.colaboradores c ON c.id = u.id_colaborador;

CREATE OR REPLACE VIEW copiloto.perfis_permissoes AS
SELECT p.id    AS perfil_id,
       p.nome  AS perfil,
       pe.nome AS permissao,
       p.ativo AS perfil_ativo
FROM public.perfis_acesso p
JOIN public.perfil_permissoes pp ON pp.id_perfil = p.id
JOIN public.permissoes pe ON pe.id = pp.id_permissao AND pe.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.auditoria AS
SELECT l.id                                                      AS registro_auditoria_id,
       l.tabela,
       l.registro_id,
       l.operacao,
       l.data_criacao                                            AS ocorrido_em,
       (l.data_criacao AT TIME ZONE 'America/Sao_Paulo')::date   AS data,
       date_trunc('month', l.data_criacao AT TIME ZONE 'America/Sao_Paulo')::date AS mes
FROM public.logs_sistema l;

-- =============================================================================================
-- SINCRONIZACAO OFFLINE (pendencias de campo)
-- =============================================================================================

CREATE OR REPLACE VIEW copiloto.sincronizacao_operacoes AS
SELECT os.id                   AS operacao_id,
       os.tipo,
       os.status,
       os.tentativas,
       os.instante_origem,
       os.data_criacao         AS recebida_em,
       initcap(c.nome_busca)   AS usuario_origem
FROM public.operacoes_sincronizacao os
LEFT JOIN public.usuarios u ON u.id = os.id_usuario_origem
LEFT JOIN public.colaboradores c ON c.id = u.id_colaborador
WHERE os.ativo = TRUE;

CREATE OR REPLACE VIEW copiloto.sincronizacao_casos AS
SELECT cs.id                   AS caso_id,
       cs.id_operacao          AS operacao_id,
       os.tipo                 AS tipo_operacao,
       cs.status,
       cs.motivo,
       cs.data_criacao         AS aberto_em,
       cs.data_resolucao,
       initcap(c.nome_busca)   AS responsavel
FROM public.casos_sincronizacao cs
LEFT JOIN public.operacoes_sincronizacao os ON os.id = cs.id_operacao
LEFT JOIN public.usuarios u ON u.id = cs.id_responsavel
LEFT JOIN public.colaboradores c ON c.id = u.id_colaborador
WHERE cs.ativo = TRUE;
