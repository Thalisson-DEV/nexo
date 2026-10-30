-- V90: Permissões dos cadastros da governança de acidentalidade.
--
-- Um par só (read/write) cobre atividades e catálogos: são o mesmo tipo de
-- manutenção, feita pelas mesmas pessoas, e separá-los criaria uma matriz de
-- perfis que ninguém usaria. A leitura é o que abastece o formulário de
-- registro de evento, por isso vai para todos os perfis do SESMT.

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:catalogo:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:catalogo:write')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome IN ('sst:catalogo:read', 'sst:catalogo:write')
ON CONFLICT DO NOTHING;

-- O coordenador mantém os cadastros; o backoffice consulta para registrar.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'COORDENADOR_SESMT'
  AND perm.nome IN ('sst:catalogo:read', 'sst:catalogo:write')
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'BACKOFFICE_SESMT'
  AND perm.nome = 'sst:catalogo:read'
ON CONFLICT DO NOTHING;
