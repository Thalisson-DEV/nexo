-- Corrige vínculos legados criados diretamente pelo módulo IAM, nos quais
-- usuarios.id_colaborador foi preenchido, mas colaboradores.id_usuario não.
UPDATE colaboradores colaborador
SET id_usuario = usuario.id
FROM usuarios usuario
WHERE usuario.id_colaborador = colaborador.id
  AND colaborador.id_usuario IS NULL;
