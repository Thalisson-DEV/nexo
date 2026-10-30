-- V38: Permissões do módulo SST (Inspeções de Segurança) e vínculo ao Administrador
INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:formulario:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:formulario:write'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:inspecao:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:inspecao:write')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome IN ('sst:formulario:read', 'sst:formulario:write', 'sst:inspecao:read', 'sst:inspecao:write')
ON CONFLICT DO NOTHING;
