-- V141: Permissão de leitura do dashboard gerencial de chamados.
--
-- A permissão nasce concedida ao perfil ADMINISTRADOR e a qualquer perfil
-- que já possua 'chamado:manage' para garantir continuidade operacional imediata.

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'chamado:dashboard:read')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome = 'chamado:dashboard:read'
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT DISTINCT pp.id_perfil, novo.id
FROM perfil_permissoes pp
JOIN permissoes existente ON existente.id = pp.id_permissao
CROSS JOIN permissoes novo
WHERE novo.nome = 'chamado:dashboard:read'
  AND existente.nome = 'chamado:manage'
ON CONFLICT DO NOTHING;
