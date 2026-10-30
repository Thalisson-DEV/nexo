-- Rastreabilidade necessária para SLA, contenção, causa raiz, reabertura e efetividade.
ALTER TABLE desvios_seguranca
    ADD COLUMN data_contencao TIMESTAMP WITH TIME ZONE,
    ADD COLUMN data_resolucao TIMESTAMP WITH TIME ZONE,
    ADD COLUMN causa_raiz VARCHAR(1000),
    ADD COLUMN data_verificacao_efetividade TIMESTAMP WITH TIME ZONE,
    ADD COLUMN acao_efetiva BOOLEAN,
    ADD COLUMN quantidade_reaberturas INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_desvios_data_resolucao
    ON desvios_seguranca(data_resolucao)
    WHERE ativo = TRUE;

CREATE INDEX idx_desvios_recorrencia
    ON desvios_seguranca(id_pergunta, status, data_resolucao)
    WHERE ativo = TRUE;
