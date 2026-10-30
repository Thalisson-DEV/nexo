-- Segregacao do fluxo tecnico de solicitacoes de material.
-- BACKOFFICE_TECNICO representa a operacao de almoxarifado no cadastro de perfis existente.
INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:create'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:plan'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:reserve'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:separate'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:deliver'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:return'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:reverse'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:close'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:import'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:audit:read')
ON CONFLICT (nome) DO NOTHING;

-- Administradores e coordenadores tecnicos recebem todas as acoes tecnicas.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome IN ('ADMINISTRADOR', 'COORDENADOR_TECNICO')
  AND permissao.nome IN (
      'estoque:solicitacao:create',
      'estoque:solicitacao:plan',
      'estoque:solicitacao:reserve',
      'estoque:solicitacao:separate',
      'estoque:solicitacao:deliver',
      'estoque:solicitacao:return',
      'estoque:solicitacao:reverse',
      'estoque:solicitacao:close',
      'estoque:solicitacao:import',
      'estoque:solicitacao:audit:read'
  )
ON CONFLICT DO NOTHING;

-- O backoffice tecnico/almoxarifado executa o fluxo operacional e pode auditar alteracoes.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome = 'BACKOFFICE_TECNICO'
  AND permissao.nome IN (
      'estoque:solicitacao:create',
      'estoque:solicitacao:plan',
      'estoque:solicitacao:reserve',
      'estoque:solicitacao:separate',
      'estoque:solicitacao:deliver',
      'estoque:solicitacao:return',
      'estoque:solicitacao:reverse',
      'estoque:solicitacao:close',
      'estoque:solicitacao:import',
      'estoque:solicitacao:audit:read'
  )
ON CONFLICT DO NOTHING;
