-- V149: Permitir tipo tecnico '221' e '222' em movimentacoes comerciais (sem obra vinculada)
ALTER TABLE movimentacoes_estoque
    DROP CONSTRAINT IF EXISTS ck_movimentacoes_estoque_contexto_tecnico_final,
    DROP CONSTRAINT IF EXISTS ck_movimentacoes_estoque_ocorrencia_final;

ALTER TABLE movimentacoes_estoque
    ADD CONSTRAINT ck_movimentacoes_estoque_contexto_tecnico_final CHECK (
        tipo_tecnico IS NULL
        OR (id_obra IS NULL AND tipo_tecnico IN ('221', '222'))
        OR (id_solicitacao IS NOT NULL AND id_operacao IS NOT NULL
            AND id_item_operacao IS NOT NULL AND id_obra IS NOT NULL
            AND tipo_obra_snapshot IS NOT NULL)
    ),
    ADD CONSTRAINT ck_movimentacoes_estoque_ocorrencia_final CHECK (
        tipo_tecnico IS NULL
        OR (id_obra IS NULL AND id_ocorrencia IS NULL AND codigo_ocorrencia_snapshot IS NULL)
        OR (tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
            AND id_ocorrencia IS NOT NULL AND codigo_ocorrencia_snapshot IS NOT NULL)
        OR (tipo_obra_snapshot IN ('OBRA', 'LPT')
            AND id_ocorrencia IS NULL AND codigo_ocorrencia_snapshot IS NULL)
    );
