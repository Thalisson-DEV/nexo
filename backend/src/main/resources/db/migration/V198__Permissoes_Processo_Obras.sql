-- =====================================================================
-- V198: Permissões do ciclo técnico de Obras (kits, planejamento sobre o
--       PDF, programação, execução, medição, documentação e central).
--
-- Liberação por permissão: as telas novas só aparecem para quem recebe
-- estas permissões. O rollback é retirar as permissões dos perfis.
--
-- Administrador recebe tudo. Supervisor (já lê e edita obras desde a
-- V109) recebe a operação do dia a dia: consulta kits, planeja, programa,
-- aponta execução, documenta e acompanha a central. Cadastro/importação de
-- kits e medição (que vira faturamento) ficam com o administrador até a
-- operação definir os perfis de engenharia e medição.
-- =====================================================================

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:kit:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:kit:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:kit:import'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:planejamento:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:planejamento:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:programacao:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:programacao:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:execucao:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:medicao:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:medicao:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:documentacao:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:documentacao:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'obras:central:read')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome IN (
    'obras:kit:read', 'obras:kit:write', 'obras:kit:import',
    'obras:planejamento:read', 'obras:planejamento:write',
    'obras:programacao:read', 'obras:programacao:write',
    'obras:execucao:write',
    'obras:medicao:read', 'obras:medicao:write',
    'obras:documentacao:read', 'obras:documentacao:write',
    'obras:central:read'
  )
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'SUPERVISOR'
  AND perm.nome IN (
    'obras:kit:read',
    'obras:planejamento:read', 'obras:planejamento:write',
    'obras:programacao:read', 'obras:programacao:write',
    'obras:execucao:write',
    'obras:medicao:read',
    'obras:documentacao:read', 'obras:documentacao:write',
    'obras:central:read'
  )
ON CONFLICT DO NOTHING;
