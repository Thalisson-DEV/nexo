-- V71: Permissões das metas de inspeção e vínculo aos perfis que já operam o SST.
INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:meta:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:meta:write')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome IN ('sst:meta:read', 'sst:meta:write')
ON CONFLICT DO NOTHING;

-- O coordenador define as metas do time; o backoffice apenas acompanha o indicador.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'COORDENADOR_SESMT'
  AND perm.nome IN ('sst:meta:read', 'sst:meta:write')
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'BACKOFFICE_SESMT'
  AND perm.nome = 'sst:meta:read'
ON CONFLICT DO NOTHING;
