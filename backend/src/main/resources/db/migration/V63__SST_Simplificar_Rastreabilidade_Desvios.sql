-- Mantém somente a data de resolução, conforme o modelo simplificado.
-- O backfill deve ocorrer antes da remoção das colunas criadas pela V62.
UPDATE desvios_seguranca
SET data_resolucao = COALESCE(data_resolucao, data_ultima_alteracao, data_criacao)
WHERE status = 'RESOLVIDO';

DROP INDEX idx_desvios_recorrencia;

ALTER TABLE desvios_seguranca
    DROP COLUMN data_contencao,
    DROP COLUMN causa_raiz,
    DROP COLUMN data_verificacao_efetividade,
    DROP COLUMN acao_efetiva,
    DROP COLUMN quantidade_reaberturas;
