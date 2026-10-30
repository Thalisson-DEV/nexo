-- V131.1: Desarma a trigger append-only de eventos_solicitacao_material antes da V132.
--
-- A trigger trg_eventos_solicitacao_append_only (criada na V124) bloqueia qualquer
-- UPDATE ou DELETE na tabela eventos_solicitacao_material.
-- Na V132, ocorrem UPDATEs de backfill na linha 180 antes do DROP TRIGGER que
-- estava posicionado apenas antes do DELETE (linha 220).
--
-- Em bancos com dados em producao, o UPDATE disparava a trigger e abortava a V132.
-- Esta migration intermediaria remove a trigger antes da V132 executar, permitindo
-- que o backfill e remodelagem ocorram sem colisoes.
--
-- A trigger e recriada ao final da V132 (e garantida pela V136 caso esta migration
-- seja executada out-of-order em ambientes onde a V132 ja havia rodado).

DROP TRIGGER IF EXISTS trg_eventos_solicitacao_append_only ON eventos_solicitacao_material;
