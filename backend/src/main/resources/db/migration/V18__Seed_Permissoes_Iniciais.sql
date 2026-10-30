-- V18: Seed de permissões iniciais e vínculo com ADMINISTRADOR

-- Inserir permissões iniciais
INSERT INTO permissoes (id, versao, data_criacao, ativo, nome) VALUES
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'iam:usuario:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'iam:usuario:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'iam:perfil:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'iam:perfil:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:colaborador:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:colaborador:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:equipe:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:equipe:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:material:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:material:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'contrato:contrato:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'contrato:contrato:write');

-- Vincular todas as permissões ao perfil ADMINISTRADOR
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pa.id, p.id
FROM perfis_acesso pa, permissoes p
WHERE pa.nome = 'ADMINISTRADOR';
