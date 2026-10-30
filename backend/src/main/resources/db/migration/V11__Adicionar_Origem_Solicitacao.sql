-- Migration V11: Adiciona o estoque de origem para rastreabilidade de saída
ALTER TABLE solicitacoes_material 
ADD COLUMN id_estoque_origem UUID REFERENCES estoques(id),
ADD COLUMN data_verificacao TIMESTAMP WITH TIME ZONE;

-- Comentário para documentação
COMMENT ON COLUMN solicitacoes_material.id_estoque_origem IS 'Estoque de onde o material será retirado (Ex: Almoxarifado Central)';
COMMENT ON COLUMN solicitacoes_material.data_verificacao IS 'Data em que o backoffice verificou validando ou rejeitando a solicitação';
