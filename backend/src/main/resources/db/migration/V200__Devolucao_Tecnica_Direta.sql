-- A devolução técnica passa a movimentar o saldo direto, sem solicitação: 222 ou 922, da obra para o
-- almoxarifado. Material fora do saldo da obra entra antes nela como entrada avulsa.
ALTER TABLE movimentacoes_estoque DROP CONSTRAINT ck_movimentacoes_estoque_contexto_tecnico_final;

ALTER TABLE movimentacoes_estoque
    ADD CONSTRAINT ck_movimentacoes_estoque_contexto_tecnico_final
        CHECK (
            tipo_tecnico IS NULL
            OR (id_obra IS NULL AND tipo_tecnico IN ('221', '222'))
            OR (
                id_solicitacao IS NOT NULL
                AND id_operacao IS NOT NULL
                AND id_item_operacao IS NOT NULL
                AND id_obra IS NOT NULL
                AND tipo_obra_snapshot IS NOT NULL
            )
            OR (
                id_solicitacao IS NULL
                AND id_operacao IS NULL
                AND id_item_operacao IS NULL
                AND id_obra IS NOT NULL
                AND tipo_obra_snapshot IS NOT NULL
                AND tipo_tecnico IN ('222', '922')
                AND tipo_movimentacao IN ('DEVOLUCAO', 'ENTRADA_AVULSA')
            )
        );
