-- =====================================================================
-- V171: Separa quem decide e quem executa as solicitações comerciais
--
-- Até aqui aprovar, reprovar, entregar e estornar dependiam todos de
-- estoque:solicitacao:write. Agora:
--
-- * estoque:solicitacao:approve (nova) aprova a solicitação pendente,
--   podendo editar os itens no momento da aprovação, e reprova a pendente.
--   Fica com o comercial (BACKOFFICE_COMERCIAL e COORDENADOR_COMERCIAL)
--   e com o ADMINISTRADOR.
-- * estoque:solicitacao:write segue com o almoxarifado: criar, rejeitar a
--   solicitação já aprovada (ex.: sem material), pendências, entrega e
--   estorno. O almoxarifado não aprova nem reprova.
--
-- Os dois lados recebem as leituras de apoio que as telas de solicitação
-- (lista, kanban, calendário, detalhe, relatório e painel) consultam.
-- Nenhuma permissão existente é removida.
-- =====================================================================

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'estoque:solicitacao:approve')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfis_acesso (id, nome, versao, data_criacao, usuario_criacao, ativo)
VALUES (gen_random_uuid(), 'BACKOFFICE_ALMOXARIFADO', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (nome) DO NOTHING;

-- O administrador continua podendo tudo, inclusive aprovar.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome = 'ADMINISTRADOR'
  AND permissao.nome = 'estoque:solicitacao:approve'
ON CONFLICT DO NOTHING;

-- Comercial: consulta todas as visões e relatórios, aprova (editando os
-- itens) e reprova. O histórico de alterações também fica visível.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome IN ('BACKOFFICE_COMERCIAL', 'COORDENADOR_COMERCIAL')
  AND permissao.nome IN (
      'estoque:solicitacao:read',
      'estoque:solicitacao:approve',
      'estoque:solicitacao:audit:read',
      -- Leituras de apoio das telas de solicitação
      'estoque:estoque:read',
      'estoque:material:read',
      'estoque:saldo:read',
      'estoque:motivo-rejeicao:read',
      'rh:colaborador:read',
      'rh:equipe:read',
      'rh:base-operacional:read',
      'rh:processo-negocio:read',
      'iam:usuario:read'
  )
ON CONFLICT DO NOTHING;

-- Almoxarifado: todo o fluxo das solicitações (comerciais e técnicas),
-- exceto aprovar e reprovar: cria, importa, rejeita a aprovada, registra
-- pendências, separa, entrega, devolve, estorna e encerra saldo.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome = 'BACKOFFICE_ALMOXARIFADO'
  AND permissao.nome IN (
      'estoque:solicitacao:read',
      'estoque:solicitacao:write',
      'estoque:solicitacao:create',
      'estoque:solicitacao:import',
      'estoque:solicitacao:plan',
      'estoque:solicitacao:reserve',
      'estoque:solicitacao:separate',
      'estoque:solicitacao:deliver',
      'estoque:solicitacao:return',
      'estoque:solicitacao:reverse',
      'estoque:solicitacao:close',
      'estoque:solicitacao:audit:read',
      -- Leituras de apoio das telas de solicitação e da entrega
      'estoque:estoque:read',
      'estoque:material:read',
      'estoque:saldo:read',
      'estoque:movimentacao:read',
      'estoque:motivo-rejeicao:read',
      'rh:colaborador:read',
      'rh:equipe:read',
      'rh:base-operacional:read',
      'rh:processo-negocio:read',
      'iam:usuario:read'
  )
ON CONFLICT DO NOTHING;
