-- Turno, APR e execução de Obras são lançados pela equipe em campo: nenhum perfil do web mantém essas permissões.
-- O perfil da equipe as recebe quando o app de Obras entrar.
DELETE FROM perfil_permissoes
WHERE id_permissao IN (SELECT id FROM permissoes WHERE nome IN ('obras:turno:write', 'obras:execucao:write'));
