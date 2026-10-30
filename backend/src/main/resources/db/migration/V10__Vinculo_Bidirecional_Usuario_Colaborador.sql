-- Adiciona a coluna id_colaborador na tabela usuarios
ALTER TABLE usuarios ADD COLUMN id_colaborador UUID;

-- Atualiza os usuários existentes com base no vínculo que já existe na tabela de colaboradores
-- (Onde colaboradores.id_usuario = usuarios.id)
UPDATE usuarios u
SET id_colaborador = c.id
FROM colaboradores c
WHERE c.id_usuario = u.id;

-- Adiciona um comentário para documentar o campo
COMMENT ON COLUMN usuarios.id_colaborador IS 'ID do Colaborador vinculado ao usuário (Redundância controlada para performance)';
