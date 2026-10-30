-- V75: permissão de administração do mural.
--
-- Só a escrita é controlada por permissão. Ler o mural não exige nenhuma: um
-- comunicado que não alcança todo o público-alvo por falta de permissão derrota o
-- propósito de existir, e o alcance já é decidido pelos segmentos do comunicado.

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'notificacao:comunicado:manage')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome = 'notificacao:comunicado:manage'
ON CONFLICT DO NOTHING;
