-- ---------------------------------------------------------
-- Feature: Preferências do Usuário (Tema e Cor)
-- Adiciona suporte para personalização da interface
-- ---------------------------------------------------------

ALTER TABLE usuarios 
ADD COLUMN tema VARCHAR(20) DEFAULT 'light',
ADD COLUMN cor_primaria VARCHAR(50) DEFAULT 'blue',
ADD COLUMN animacoes_ativadas BOOLEAN DEFAULT TRUE;

-- Atualiza registros existentes para garantir o padrão
UPDATE usuarios SET tema = 'light', cor_primaria = 'blue', animacoes_ativadas = TRUE WHERE tema IS NULL;
