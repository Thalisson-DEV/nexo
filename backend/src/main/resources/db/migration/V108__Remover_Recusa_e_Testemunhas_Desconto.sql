-- V108: Remover tabela de testemunhas e coluna de recusa do colaborador do módulo de desconto
DROP TABLE IF EXISTS desconto_testemunha CASCADE;
ALTER TABLE desconto DROP COLUMN IF EXISTS recusa_colaborador;
