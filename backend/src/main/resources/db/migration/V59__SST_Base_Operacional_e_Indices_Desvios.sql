-- Vincula a inspeção de SST à base operacional em que foi realizada.
-- A coluna permanece anulável apenas para preservar inspeções históricas;
-- a API exige o preenchimento em todos os novos lançamentos.
ALTER TABLE inspecoes_seguranca
    ADD COLUMN id_base_operacional UUID;

CREATE INDEX idx_inspecoes_base_operacional
    ON inspecoes_seguranca(id_base_operacional);

-- Índices compostos para a caixa de pendências do colaborador.
CREATE INDEX idx_desvio_colab_colaborador_desvio
    ON desvio_colaboradores(id_colaborador, id_desvio)
    WHERE ativo = TRUE;

CREATE INDEX idx_desvios_status_prazo
    ON desvios_seguranca(status, prazo)
    WHERE ativo = TRUE;
