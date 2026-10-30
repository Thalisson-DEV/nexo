-- V45: Adicionar relacionamento de Processo de Negócio ao cadastro de Materiais

ALTER TABLE materiais ADD COLUMN id_processo UUID REFERENCES processos_negocio(id);

-- Para os materiais existentes, define como padrão o processo COMERCIAL
UPDATE materiais 
SET id_processo = (SELECT id FROM processos_negocio WHERE nome = 'COMERCIAL' LIMIT 1)
WHERE id_processo IS NULL;

-- Garante que o processo seja obrigatório após preencher o default
ALTER TABLE materiais ALTER COLUMN id_processo SET NOT NULL;

-- Índice para otimização de busca/filtro por processo na listagem de materiais
CREATE INDEX idx_materiais_id_processo ON materiais(id_processo);
