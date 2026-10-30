-- =====================================================================
-- V92: Permissões dos eventos de SST e as regras de fábrica que avisam
--      o SESMT quando algo acontece.
--
-- O par read/write segue o desenho da V90: quem registra um acidente é
-- quem trata, e separar "registrar" de "tratar" criaria uma matriz de
-- perfis que ninguém usaria. O dashboard continua sob sst:dashboard:read,
-- que já existe — indicador de acidentalidade é a mesma audiência dos
-- demais indicadores de SST.
-- =====================================================================

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:evento:read'),
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'sst:evento:write')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome IN ('sst:evento:read', 'sst:evento:write')
ON CONFLICT DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'COORDENADOR_SESMT'
  AND perm.nome IN ('sst:evento:read', 'sst:evento:write')
ON CONFLICT DO NOTHING;

-- O backoffice do SESMT é quem registra a ocorrência comunicada pela
-- operação, então também escreve.
INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'BACKOFFICE_SESMT'
  AND perm.nome IN ('sst:evento:read', 'sst:evento:write')
ON CONFLICT DO NOTHING;

-- ═══════════════════════════════════════════════════════════════════════
-- Regras de fábrica
--
-- Nascem INATIVAS, ao contrário das regras por ocorrência da V79. O motivo
-- não é receio de ruído — estas só disparam quando um acidente de fato
-- aconteceu — e sim o deeplink: a tela /sst/eventos/{id} ainda não existe
-- no frontend. Aviso com link morto ensina o usuário a ignorar o aviso.
-- Basta ligá-las pela tela de regras quando a tela entrar no ar.
--
-- Cooldown zero, como nas demais regras por ocorrência: o escopo do
-- silêncio seria o registro, e nenhum evento dispara duas vezes.
--
-- O destinatário é a PERMISSÃO, e não uma lista de nomes: quando alguém
-- entra ou sai do SESMT, os destinatários acompanham sozinhos.
--
-- Nenhum nome de pessoa entra no texto. O contexto do evento não carrega
-- o nome do acidentado de propósito — notificação chega ao sininho e ao
-- e-mail, e quem tem permissão de ver quem se machucou abre o registro
-- pelo deeplink. A rota está especificada em docs/spec-frontend-sst-eventos.md.
-- ═══════════════════════════════════════════════════════════════════════

WITH nova_regra AS (
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        versao, data_criacao, usuario_criacao, ativo)
    VALUES (
        gen_random_uuid(), 'sst.evento.registrado',
        'Evento de segurança registrado',
        'Avisa o SESMT a cada ocorrência de segurança comunicada.',
        TRUE, 'SEGURANCA', 'ALTA',
        '{{tipoRotulo}} registrado: {{numeroRegistro}}',
        'Ocorrência {{numeroRegistro}} ({{tipoRotulo}}) na atividade {{atividade}}, base {{base}}.',
        '/sst/eventos/{{idEvento}}', NULL, 0,
        0, CURRENT_TIMESTAMP, 'SISTEMA', FALSE)
    RETURNING id
)
INSERT INTO destinatarios_regra_notificacao (
    id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), nova_regra.id, 'PERMISSAO', NULL, 'sst:evento:write',
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM nova_regra;

WITH nova_regra AS (
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        versao, data_criacao, usuario_criacao, ativo)
    VALUES (
        gen_random_uuid(), 'sst.evento.concluido',
        'Evento de segurança concluído',
        'Fecha o ciclo: quem soube do acidente sabe o que foi feito a respeito.',
        TRUE, 'SEGURANCA', 'NORMAL',
        'Tratativa concluída: {{numeroRegistro}}',
        'A ocorrência {{numeroRegistro}} ({{tipoRotulo}}) na base {{base}} teve a tratativa encerrada.',
        '/sst/eventos/{{idEvento}}', NULL, 0,
        0, CURRENT_TIMESTAMP, 'SISTEMA', FALSE)
    RETURNING id
)
INSERT INTO destinatarios_regra_notificacao (
    id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), nova_regra.id, 'PERMISSAO', NULL, 'sst:evento:read',
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM nova_regra;
