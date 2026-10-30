-- V14: Adicionar campo de número de série para rastreabilidade no Kardex
-- Garantindo que o histórico de movimentações suporte materiais com controle de série.

ALTER TABLE movimentacoes_estoque 
ADD COLUMN num_serie TEXT; -- 🔒 Cifrado (AES-256 GCM)

COMMENT ON COLUMN movimentacoes_estoque.num_serie IS 'Número de série do material, cifrado para auditoria e rastreabilidade individual.';
