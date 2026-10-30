-- V12: Adicionar suporte para entrega parcial de solicitações de material
ALTER TABLE itens_solicitacao ADD COLUMN quantidade_entregue NUMERIC(12, 3) NOT NULL DEFAULT 0;

-- Atualizar o check constraint de status na tabela solicitacoes_material se houver (o Hibernate costuma usar apenas o nome da string, mas se houver validacao no DB, precisamos ajustar)
-- Como o sistema usa @Enumerated(EnumType.STRING), o banco aceita qualquer string, mas o domínio garante a integridade.
