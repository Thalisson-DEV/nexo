-- V21: Correção de Premissas de Ativos e Rastreabilidade
-- Remove a criptografia de campos que exigem busca parcial (LIKE) e refatora estrutura de execuções

-- 1. Refatoração da Tabela de Execuções (Mudança de Cabeçalho para Itens)
ALTER TABLE execucoes_comercial DROP COLUMN IF EXISTS num_serie_medidor;

-- 2. Adição de num_serie em execucoes_materiais (Para suportar N materiais com série em uma execução)
ALTER TABLE execucoes_materiais ADD COLUMN num_serie VARCHAR(255);
COMMENT ON COLUMN execucoes_materiais.num_serie IS 'Número de série do material (ex: medidor). Aberto para busca parcial (LIKE).';

-- 3. Correção de Logística: Itens de Solicitação (Removendo premissa de criptografia)
-- O campo já existe, vamos apenas garantir o tipo e limpar o comentário restritivo
ALTER TABLE itens_solicitacao ALTER COLUMN num_serie TYPE VARCHAR(255);
COMMENT ON COLUMN itens_solicitacao.num_serie IS 'Número de série do material solicitado/entregue. Aberto para busca parcial (LIKE).';

-- 4. Correção de Logística: Movimentações de Estoque (Removendo premissa de criptografia)
ALTER TABLE movimentacoes_estoque ALTER COLUMN num_serie TYPE VARCHAR(255);
COMMENT ON COLUMN movimentacoes_estoque.num_serie IS 'Número de série do material na movimentação de estoque. Aberto para busca parcial (LIKE).';

-- 5. Limpeza de Metadados de Segurança em Ativos (Poste e Posto)
-- Estes campos agora são texto puro para permitir filtros de Specification (LIKE)
COMMENT ON COLUMN execucoes_comercial.num_poste IS 'Identificação do Poste (Texto Puro - Permite LIKE)';
COMMENT ON COLUMN execucoes_comercial.num_posto IS 'Identificação do Posto/Transformador (Texto Puro - Permite LIKE)';

-- 6. Criação de Índices de Performance para buscas parciais (B-Tree padrão é eficiente para LIKE 'abc%')
CREATE INDEX idx_exec_materiais_num_serie ON execucoes_materiais(num_serie);
CREATE INDEX idx_itens_solicitacao_num_serie ON itens_solicitacao(num_serie);
CREATE INDEX idx_movimentacoes_num_serie ON movimentacoes_estoque(num_serie);
