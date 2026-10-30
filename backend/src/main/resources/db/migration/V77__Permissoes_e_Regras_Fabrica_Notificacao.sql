-- V77: permissão de administração das regras e as regras de fábrica.
--
-- As regras nascem com sistema = TRUE: podem ser editadas (limiar, texto, cooldown,
-- destinatários) e desativadas, mas não removidas. Sem isso, apagar a notificação de
-- aprovação pendente passa despercebido até a fila estourar.
--
-- Todas nascem DESATIVADAS (ativo = FALSE), de propósito: a organização decide o
-- limiar e quem recebe antes de o sistema começar a avisar. Uma regra de fábrica
-- ligada com valores adivinhados por nós geraria ruído no primeiro dia e ensinaria
-- todo mundo a ignorar o sininho.

INSERT INTO permissoes (id, versao, data_criacao, usuario_criacao, ativo, nome)
VALUES
    (gen_random_uuid(), 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE, 'notificacao:regra:manage')
ON CONFLICT (nome) DO NOTHING;

INSERT INTO perfil_permissoes (id_perfil, id_permissao)
SELECT p.id, perm.id
FROM perfis_acesso p
CROSS JOIN permissoes perm
WHERE p.nome = 'ADMINISTRADOR'
  AND perm.nome = 'notificacao:regra:manage'
ON CONFLICT DO NOTHING;


-- 1) Descontos aguardando aprovação — o exemplo que motivou o módulo.
WITH nova_regra AS (
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        versao, data_criacao, usuario_criacao, ativo)
    VALUES (
        gen_random_uuid(), 'desconto.pendente_aprovacao',
        'Descontos pendentes de aprovação',
        'Avisa quem aprova descontos quando a fila atinge o limiar configurado.',
        TRUE, 'APROVACAO', 'NORMAL',
        'Descontos aguardando sua aprovação',
        'Há {{total}} desconto(s) solicitado(s) aguardando análise.',
        '/descontos?status=SOLICITADO', 1, 240,
        0, CURRENT_TIMESTAMP, 'SISTEMA', FALSE)
    RETURNING id
)
INSERT INTO destinatarios_regra_notificacao (
    id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), nova_regra.id, 'PERMISSAO', NULL, 'desconto:validar',
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM nova_regra;


-- 2) Desvios de segurança pendentes — o outro exemplo, com limiar maior porque
--    desvio isolado é rotina; acúmulo é que pede atenção da gestão.
WITH nova_regra AS (
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        versao, data_criacao, usuario_criacao, ativo)
    VALUES (
        gen_random_uuid(), 'sst.desvios_pendentes',
        'Desvios de segurança acumulados',
        'Avisa a gestão de segurança quando os desvios não resolvidos atingem o limiar.',
        TRUE, 'SEGURANCA', 'ALTA',
        'Desvios de segurança exigem atenção',
        'Há {{total}} desvio(s) de segurança sem resolução, acima do limite de {{limiar}}.',
        '/sst/desvios?status=ABERTO', 10, 720,
        0, CURRENT_TIMESTAMP, 'SISTEMA', FALSE)
    RETURNING id
)
INSERT INTO destinatarios_regra_notificacao (
    id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), nova_regra.id, 'PERMISSAO', NULL, 'sst:desvio:tratar',
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM nova_regra;


-- 3) Solicitações financeiras submetidas e sem decisão.
WITH nova_regra AS (
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        versao, data_criacao, usuario_criacao, ativo)
    VALUES (
        gen_random_uuid(), 'financeiro.solicitacoes_pendentes',
        'Solicitações financeiras pendentes',
        'Avisa quem tem alçada quando compras e pagamentos submetidos se acumulam.',
        TRUE, 'APROVACAO', 'NORMAL',
        'Solicitações financeiras aguardando decisão',
        'Há {{total}} solicitação(ões) de compra ou pagamento aguardando aprovação.',
        '/financeiro/solicitacoes?status=SOLICITADO', 1, 240,
        0, CURRENT_TIMESTAMP, 'SISTEMA', FALSE)
    RETURNING id
)
INSERT INTO destinatarios_regra_notificacao (
    id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), nova_regra.id, 'PERMISSAO', NULL, 'financeiro:compra:aprovar',
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM nova_regra;


-- 4) Chamados na fila de atendimento.
WITH nova_regra AS (
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        versao, data_criacao, usuario_criacao, ativo)
    VALUES (
        gen_random_uuid(), 'chamado.pendentes_atendimento',
        'Chamados na fila',
        'Avisa a central quando os chamados sem resolução atingem o limiar.',
        TRUE, 'CHAMADO', 'NORMAL',
        'Chamados aguardando atendimento',
        'Há {{total}} chamado(s) na fila, acima do limite de {{limiar}}.',
        '/chamados?status=ABERTO', 5, 480,
        0, CURRENT_TIMESTAMP, 'SISTEMA', FALSE)
    RETURNING id
)
INSERT INTO destinatarios_regra_notificacao (
    id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), nova_regra.id, 'PERMISSAO', NULL, 'chamado:manage',
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM nova_regra;


-- 5) Desconto solicitado — a única por ocorrência, e o exemplo de PAPEL_DINAMICO:
--    o solicitante é conhecido só dentro do evento, não por cadastro.
WITH nova_regra AS (
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        versao, data_criacao, usuario_criacao, ativo)
    VALUES (
        gen_random_uuid(), 'desconto.solicitado',
        'Confirmação de desconto registrado',
        'Confirma ao solicitante que o desconto foi registrado e aguarda validação.',
        TRUE, 'APROVACAO', 'BAIXA',
        'Desconto registrado',
        'Seu desconto por {{motivo}} em {{parcelas}}x foi registrado e aguarda validação.',
        '/descontos/{{idDesconto}}', NULL, 0,
        0, CURRENT_TIMESTAMP, 'SISTEMA', FALSE)
    RETURNING id
)
INSERT INTO destinatarios_regra_notificacao (
    id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), nova_regra.id, 'PAPEL_DINAMICO', NULL, 'solicitante',
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM nova_regra;
