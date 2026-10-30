-- Um estoque patrimonial por obra. Nullable para manter estoques legados.
ALTER TABLE estoques
    ADD COLUMN id_obra UUID;

CREATE UNIQUE INDEX uq_estoques_id_obra
    ON estoques(id_obra)
    WHERE id_obra IS NOT NULL;

-- Correlacoes tecnicas nullable: movimentos comerciais existentes continuam
-- validos sem agrupador, filho, reserva, operacao, obra ou equipe.
ALTER TABLE movimentacoes_estoque
    ADD COLUMN id_agrupador UUID
        REFERENCES agrupamentos_solicitacao_tecnica(id) ON DELETE RESTRICT,
    ADD COLUMN id_solicitacao UUID
        REFERENCES solicitacoes_material(id) ON DELETE RESTRICT,
    ADD COLUMN tipo_tecnico VARCHAR(3),
    ADD COLUMN tipo_obra_snapshot VARCHAR(20),
    ADD COLUMN ocorrencia VARCHAR(100),
    ADD COLUMN id_reserva UUID
        REFERENCES reservas_solicitacao_tecnica(id) ON DELETE RESTRICT,
    ADD COLUMN id_operacao UUID
        REFERENCES operacoes_solicitacao_material(id) ON DELETE RESTRICT,
    ADD COLUMN id_item_operacao UUID
        REFERENCES itens_operacao_solicitacao(id) ON DELETE RESTRICT,
    ADD COLUMN id_obra UUID,
    ADD COLUMN id_equipe UUID;

ALTER TABLE movimentacoes_estoque
    ADD CONSTRAINT ck_movimentacoes_estoque_tipo_tecnico
        CHECK (tipo_tecnico IS NULL OR tipo_tecnico IN ('221', '921', '222', '922')),
    ADD CONSTRAINT ck_movimentacoes_estoque_tipo_obra
        CHECK (tipo_obra_snapshot IS NULL OR tipo_obra_snapshot IN ('OBRA', 'CORRETIVA', 'PREVENTIVA', 'LPT'));

CREATE INDEX idx_movimentacoes_estoque_agrupador
    ON movimentacoes_estoque(id_agrupador);
CREATE INDEX idx_movimentacoes_estoque_solicitacao
    ON movimentacoes_estoque(id_solicitacao);
CREATE INDEX idx_movimentacoes_estoque_tipo_tecnico
    ON movimentacoes_estoque(tipo_tecnico);
CREATE INDEX idx_movimentacoes_estoque_reserva
    ON movimentacoes_estoque(id_reserva);
CREATE INDEX idx_movimentacoes_estoque_operacao
    ON movimentacoes_estoque(id_operacao);
CREATE INDEX idx_movimentacoes_estoque_item_operacao
    ON movimentacoes_estoque(id_item_operacao);
CREATE INDEX idx_movimentacoes_estoque_obra
    ON movimentacoes_estoque(id_obra);
CREATE INDEX idx_movimentacoes_estoque_equipe
    ON movimentacoes_estoque(id_equipe);
