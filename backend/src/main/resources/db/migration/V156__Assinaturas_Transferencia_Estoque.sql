ALTER TABLE movimentacoes_estoque
    ADD COLUMN IF NOT EXISTS assinatura_entregador TEXT,
    ADD COLUMN IF NOT EXISTS assinatura_recebedor TEXT;

COMMENT ON COLUMN movimentacoes_estoque.assinatura_entregador IS 'Assinatura cifrada ou tipo FISICA do estoque entregador (origem) na transferência.';
COMMENT ON COLUMN movimentacoes_estoque.assinatura_recebedor IS 'Assinatura cifrada ou tipo FISICA do estoque recebedor (destino) na transferência.';
