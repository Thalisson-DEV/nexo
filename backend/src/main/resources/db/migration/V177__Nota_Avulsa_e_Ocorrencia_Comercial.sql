-- =====================================================================
-- V177: Nota avulsa e ocorrência comercial
--
-- O lançamento de campo passa a ter três tipos:
-- * COMUM: a nota de sempre, com o número da OS ou do talão.
-- * AVULSA: nota encontrada em campo, ainda sem número. O app gera um
--   número provisório (guardado em referencia_provisoria) e o número
--   oficial é informado depois; data_numero_oficial marca quando.
-- * OCORRENCIA: ocorrência comercial, identificada pelo número da
--   ocorrência (letras e números). Cada material diz a origem: EQUIPE
--   movimenta o saldo da equipe como na nota comum; CONCESSIONARIA
--   (material da Concessionária) fica só como registro e nunca toca o saldo.
--
-- Os três tipos contam faturamento e meta pelos serviços lançados.
-- Tudo o que já foi lançado é COMUM, com materiais da EQUIPE.
-- =====================================================================

ALTER TABLE execucoes_comercial
    ADD COLUMN tipo_nota VARCHAR(20) NOT NULL DEFAULT 'COMUM',
    ADD COLUMN referencia_provisoria VARCHAR(40),
    ADD COLUMN data_numero_oficial TIMESTAMP WITH TIME ZONE,
    ADD COLUMN id_usuario_numero_oficial UUID,
    ADD CONSTRAINT ck_execucoes_comercial_tipo_nota
        CHECK (tipo_nota IN ('COMUM', 'AVULSA', 'OCORRENCIA'));

-- A fila do escritório: avulsas que ainda esperam o número oficial.
CREATE INDEX ix_execucoes_comercial_avulsa_sem_numero
    ON execucoes_comercial (data_lancamento)
    WHERE tipo_nota = 'AVULSA' AND data_numero_oficial IS NULL AND ativo;

ALTER TABLE execucoes_materiais
    ADD COLUMN origem VARCHAR(20) NOT NULL DEFAULT 'EQUIPE',
    ADD CONSTRAINT ck_execucoes_materiais_origem
        CHECK (origem IN ('EQUIPE', 'CONCESSIONARIA'));

-- Camada do Copiloto: CREATE OR REPLACE VIEW só acrescenta colunas no fim.
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
       COALESCE(ts.descricao, 'NÃO INFORMADO')                 AS tipo_servico,
       ex.tipo_nota,
       (ex.tipo_nota = 'AVULSA' AND ex.data_numero_oficial IS NULL) AS aguardando_numero_oficial
FROM public.execucoes_comercial ex
LEFT JOIN public.equipes eq ON eq.id = ex.id_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
LEFT JOIN public.municipios mu ON mu.id = ex.id_municipio
LEFT JOIN public.tipos_servico ts ON ts.id = ex.id_tipo_servico
WHERE ex.ativo = TRUE;

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
       b.nome                                                  AS base_operacional,
       em.origem,
       ex.tipo_nota
FROM public.execucoes_materiais em
JOIN public.execucoes_comercial ex ON ex.id = em.id_execucao
JOIN public.materiais m ON m.id = em.id_material
LEFT JOIN public.equipes eq ON eq.id = ex.id_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
WHERE em.ativo = TRUE AND ex.ativo = TRUE;
