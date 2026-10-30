-- =====================================================================
-- V174: Encerramento automático de turnos esquecidos
--
-- Turno que a equipe esquece aberto é encerrado pelo servidor no horário de
-- corte do dia. O fim registrado é o do último lançamento do turno (ou a própria
-- abertura, quando não houve lançamento), para a jornada não inflar as análises.
-- O turno guarda que foi encerrado assim e o porquê, como informativo.
-- =====================================================================

ALTER TABLE turnos
    ADD COLUMN encerrado_automaticamente BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN motivo_encerramento VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_turnos_abertos_data_inicio
    ON turnos (data_inicio)
    WHERE status = 'ABERTO' AND ativo = TRUE;

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
       (SELECT count(*) FROM public.execucoes_comercial ex WHERE ex.id_turno = t.id AND ex.ativo = TRUE) AS total_execucoes,
       t.encerrado_automaticamente,
       t.motivo_encerramento
FROM public.turnos t
LEFT JOIN public.equipes eq ON eq.id = t.id_equipe
LEFT JOIN public.bases_operacionais b ON b.id = eq.id_base_operacional
LEFT JOIN public.usuarios uab ON uab.id = t.id_usuario_abertura
LEFT JOIN public.colaboradores cab ON cab.id = uab.id_colaborador
WHERE t.ativo = TRUE;
