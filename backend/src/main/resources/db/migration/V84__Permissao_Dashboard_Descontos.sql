-- V84: Permissão de leitura do dashboard executivo de descontos.
--
-- A permissão nasce separada de 'desconto:read' porque é o que permite retirá-la de
-- alguém no futuro sem tirar junto o acesso à listagem. Mas ela nasce *concedida* a
-- quem já opera o domínio: quem consulta os lançamentos ou quem valida os termos está
-- respondendo pelos mesmos números que o dashboard agrega, e obrigar um segundo
-- cadastro de perfil para ver o consolidado do próprio trabalho só produziria
-- chamado de acesso.

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'desconto:dashboard:read')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome = 'desconto:dashboard:read'
ON CONFLICT DO NOTHING;

-- Herda para todo perfil que já consulta, valida ou dá baixa em descontos. O DISTINCT
-- é necessário: um mesmo perfil costuma ter as três permissões, e sem ele a linha
-- seria proposta três vezes.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT DISTINCT pp.id_perfil, novo.id
FROM perfil_permissoes pp
JOIN permissoes existente ON existente.id = pp.id_permissao
CROSS JOIN permissoes novo
WHERE novo.nome = 'desconto:dashboard:read'
  AND existente.nome IN ('desconto:read', 'desconto:validar', 'desconto:baixar')
ON CONFLICT DO NOTHING;
