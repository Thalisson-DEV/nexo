-- V67: cria os perfis operacionais do SESMT e vincula as permissoes necessarias
-- para que cada fluxo funcione de ponta a ponta na interface.

INSERT INTO perfis_acesso (
    id, nome, versao, data_criacao, usuario_criacao, ativo
)
VALUES
    (gen_random_uuid(), 'COORDENADOR_SESMT', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    (gen_random_uuid(), 'BACKOFFICE_SESMT', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (nome) DO NOTHING;

-- COORDENADOR_SESMT
--
-- Chamados: chamado:read e chamado:write formam o autosservico completo, incluindo
-- a leitura dos topicos, abertura, acompanhamento, interacoes, anexos e reabertura.
--
-- Descontos: alem das acoes solicitadas, desconto:read permite localizar o registro
-- para edicao/anexo e consultar ou baixar seus documentos. As leituras de motivo,
-- tipo de equipamento e colaborador alimentam os campos de apoio do lancamento.
--
-- IAM: a leitura de usuarios permite chegar ao cadastro e selecionar o registro a
-- editar; perfil e colaborador sao as duas referencias aceitas pelo formulario.
-- Nao sao concedidas escritas de perfil ou permissao.
--
-- SST: formulario:read e inspecao:read sao contrapartes necessarias das escritas.
-- As leituras de RH correspondem a todas as referencias do lancamento de inspecao:
-- processo, veiculo, equipe, base operacional e colaboradores inspecionados.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome = 'COORDENADOR_SESMT'
  AND permissao.nome IN (
      'chamado:read',
      'chamado:write',
      'desconto:solicitar',
      'desconto:editar',
      'desconto:anexar',
      'desconto:read',
      'desconto:motivo:read',
      'desconto:tipo-equipamento:read',
      'iam:usuario:read',
      'iam:usuario:write',
      'iam:perfil:read',
      'sst:formulario:read',
      'sst:formulario:write',
      'sst:inspecao:read',
      'sst:inspecao:write',
      'sst:dashboard:read',
      'rh:processo-negocio:read',
      'rh:veiculo:read',
      'rh:equipe:read',
      'rh:base-operacional:read',
      'rh:colaborador:read'
  )
ON CONFLICT DO NOTHING;

-- BACKOFFICE_SESMT
--
-- O perfil recebe o mesmo autosservico de chamados. Em descontos, solicitar cria
-- somente o cabecalho do termo; editar e necessario para incluir, corrigir ou remover
-- os itens durante o lancamento. A leitura do desconto permite recarregar e retomar o
-- termo, enquanto motivo, tipo de equipamento e colaborador preenchem seus campos de
-- apoio. Nao recebe anexos, validacao, rejeicao, baixa ou escrita nos catalogos.
--
-- Em SST, formulario:read e as leituras de RH tornam possivel preencher a inspecao.
-- inspecao:read e necessaria para acompanhar o lancamento e localizar/tratar desvios.
-- A leitura de usuarios alimenta o filtro de inspetor dos dashboards de SST.
-- Nao recebe formulario:write, preservando a administracao dos formularios no perfil
-- de coordenacao.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.nome = 'BACKOFFICE_SESMT'
  AND permissao.nome IN (
      'chamado:read',
      'chamado:write',
      'desconto:solicitar',
      'desconto:editar',
      'desconto:read',
      'desconto:motivo:read',
      'desconto:tipo-equipamento:read',
      'sst:formulario:read',
      'sst:inspecao:read',
      'sst:inspecao:write',
      'sst:dashboard:read',
      'iam:usuario:read',
      'rh:processo-negocio:read',
      'rh:veiculo:read',
      'rh:equipe:read',
      'rh:base-operacional:read',
      'rh:colaborador:read'
  )
ON CONFLICT DO NOTHING;
