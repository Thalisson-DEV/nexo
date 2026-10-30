-- Simplificação do modelo: Equipe → Contrato direto.
-- Coeficiente vira propriedade do próprio contrato, base sai do contrato,
-- e a tabela N:N contrato_base_faturamento é descontinuada.

ALTER TABLE contratos
    ADD COLUMN coeficiente_faturamento NUMERIC(10,4) NOT NULL DEFAULT 1.0
    CHECK (coeficiente_faturamento > 0 AND coeficiente_faturamento <= 1000);

ALTER TABLE contratos DROP COLUMN id_base_operacional;

DROP TABLE IF EXISTS contrato_base_faturamento;

ALTER TABLE equipes
    ADD COLUMN id_contrato UUID NULL REFERENCES contratos(id);

CREATE INDEX idx_equipes_id_contrato ON equipes(id_contrato);
