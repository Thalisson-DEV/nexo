-- =====================================================================
-- V201: A carteira usa o status da obra
--
-- A situação na carteira (V196) era um texto livre com o mesmo significado
-- do status da obra. O status cadastrado é o único: a importação da carteira
-- passa a atualizá-lo.
-- =====================================================================

ALTER TABLE obras DROP COLUMN situacao_carteira;
