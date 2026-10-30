-- V19: Mapeamento Total de Permissões de todos os módulos
-- IAM, RH, ESTOQUE e CONTRATO

-- Inserir permissões de IAM restantes
INSERT INTO permissoes (id, versao, data_criacao, ativo, nome) VALUES
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'iam:audit:read')
ON CONFLICT (nome) DO NOTHING;

-- Inserir permissões de RH restantes
INSERT INTO permissoes (id, versao, data_criacao, ativo, nome) VALUES
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:base-operacional:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:base-operacional:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:centro-faturamento:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:centro-faturamento:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:processo-negocio:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:processo-negocio:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:municipio:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:municipio:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:funcao:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:funcao:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:veiculo:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:veiculo:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:tipo-equipe:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'rh:tipo-equipe:write')
ON CONFLICT (nome) DO NOTHING;

-- Inserir permissões de ESTOQUE restantes
INSERT INTO permissoes (id, versao, data_criacao, ativo, nome) VALUES
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:estoque:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:estoque:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:movimentacao:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:movimentacao:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:saldo:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:solicitacao:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:solicitacao:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:centro-investimento:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:centro-investimento:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:avaliacao-material:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:avaliacao-material:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:motivo-rejeicao:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:motivo-rejeicao:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:tipo-estoque:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'estoque:tipo-estoque:write')
ON CONFLICT (nome) DO NOTHING;

-- Inserir permissões de CONTRATO
INSERT INTO permissoes (id, versao, data_criacao, ativo, nome) VALUES
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'contrato:acao:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'contrato:acao:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'contrato:acao-valor:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'contrato:acao-valor:write'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'contrato:contrato:read'),
(gen_random_uuid(), 0, CURRENT_TIMESTAMP, TRUE, 'contrato:contrato:write')
ON CONFLICT (nome) DO NOTHING;

-- Vincular TODAS as permissões ao perfil ADMINISTRADOR
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT pa.id, p.id
FROM perfis_acesso pa, permissoes p
WHERE pa.nome = 'ADMINISTRADOR'
ON CONFLICT DO NOTHING;
