-- Adiciona a coluna condicao nas tabelas de saldo e movimentação
ALTER TABLE saldos ADD COLUMN condicao VARCHAR(20) NOT NULL DEFAULT 'NOVO';
ALTER TABLE saldos_seriais ADD COLUMN condicao VARCHAR(20) NOT NULL DEFAULT 'NOVO';
ALTER TABLE movimentacoes_estoque ADD COLUMN condicao VARCHAR(20) NOT NULL DEFAULT 'NOVO';

-- Atualiza a constraint única de saldos para incluir a condição
ALTER TABLE saldos DROP CONSTRAINT unq_estoque_material;
ALTER TABLE saldos ADD CONSTRAINT unq_estoque_material_condicao UNIQUE (id_estoque, id_material, condicao);

-- Cria um índice para busca por condição em saldos_seriais e movimentacoes
CREATE INDEX idx_saldos_seriais_condicao ON saldos_seriais(condicao);
CREATE INDEX idx_movimentacoes_condicao ON movimentacoes_estoque(condicao);
