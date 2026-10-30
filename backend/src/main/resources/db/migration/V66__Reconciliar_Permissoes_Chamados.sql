-- V66: reconcilia as permissoes de chamados em ambientes onde a migration foi
-- aplicada enquanto as authorities dos perfis ainda estavam armazenadas no Redis.
-- A migration e idempotente para tambem reparar vinculos removidos ou ausentes.

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
SELECT gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, nova.nome
FROM (VALUES
    ('chamado:read'),
    ('chamado:write'),
    ('chamado:manage')
) AS nova(nome)
WHERE NOT EXISTS (
    SELECT 1 FROM permissoes existente WHERE existente.nome = nova.nome
);

-- Autosservico para todos os perfis ativos.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
CROSS JOIN permissoes permissao
WHERE perfil.ativo = TRUE
  AND permissao.nome IN ('chamado:read', 'chamado:write')
  AND NOT EXISTS (
      SELECT 1
      FROM perfil_permissoes existente
      WHERE existente.id_perfil = perfil.id
        AND existente.id_permissao = permissao.id
  );

-- Administracao completa para o perfil administrador, tolerando espacos e caixa.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT perfil.id, permissao.id
FROM perfis_acesso perfil
JOIN permissoes permissao ON permissao.nome = 'chamado:manage'
WHERE UPPER(TRIM(perfil.nome)) = 'ADMINISTRADOR'
  AND perfil.ativo = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM perfil_permissoes existente
      WHERE existente.id_perfil = perfil.id
        AND existente.id_permissao = permissao.id
  );
