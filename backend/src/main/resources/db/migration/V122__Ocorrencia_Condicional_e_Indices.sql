-- Agrupadores sao sempre tecnicos.
ALTER TABLE agrupamentos_solicitacao_tecnica
    ADD CONSTRAINT ck_agrupamentos_ocorrencia_por_tipo_obra
        CHECK (
            (
                tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
                AND ocorrencia IS NOT NULL
                AND btrim(ocorrencia) <> ''
            )
            OR (
                tipo_obra_snapshot IN ('OBRA', 'LPT')
                AND ocorrencia IS NULL
            )
        );

ALTER TABLE solicitacoes_material
    ADD CONSTRAINT ck_solicitacoes_material_ocorrencia_por_tipo_obra
        CHECK (
            contexto IS DISTINCT FROM 'TECNICO'
            OR (
                tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
                AND ocorrencia IS NOT NULL
                AND btrim(ocorrencia) <> ''
            )
            OR (
                tipo_obra_snapshot IN ('OBRA', 'LPT')
                AND ocorrencia IS NULL
            )
        );

ALTER TABLE operacoes_solicitacao_material
    ADD CONSTRAINT ck_operacoes_solicitacao_ocorrencia_por_tipo_obra
        CHECK (
            (
                tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
                AND ocorrencia IS NOT NULL
                AND btrim(ocorrencia) <> ''
            )
            OR (
                tipo_obra_snapshot IN ('OBRA', 'LPT')
                AND ocorrencia IS NULL
            )
        );

ALTER TABLE movimentacoes_estoque
    ADD CONSTRAINT ck_movimentacoes_estoque_contexto_tecnico
        CHECK (
            tipo_tecnico IS NULL
            OR (
                id_agrupador IS NOT NULL
                AND id_solicitacao IS NOT NULL
                AND id_operacao IS NOT NULL
                AND id_item_operacao IS NOT NULL
                AND id_obra IS NOT NULL
                AND tipo_obra_snapshot IS NOT NULL
            )
        ),
    ADD CONSTRAINT ck_movimentacoes_estoque_ocorrencia_por_tipo_obra
        CHECK (
            tipo_tecnico IS NULL
            OR (
                tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
                AND ocorrencia IS NOT NULL
                AND btrim(ocorrencia) <> ''
            )
            OR (
                tipo_obra_snapshot IN ('OBRA', 'LPT')
                AND ocorrencia IS NULL
            )
        );

CREATE INDEX idx_solicitacoes_material_obra_ocorrencia
    ON solicitacoes_material(id_obra, ocorrencia)
    WHERE contexto = 'TECNICO' AND ocorrencia IS NOT NULL;

CREATE INDEX idx_movimentacoes_estoque_obra_ocorrencia
    ON movimentacoes_estoque(id_obra, ocorrencia)
    WHERE tipo_tecnico IS NOT NULL AND ocorrencia IS NOT NULL;
