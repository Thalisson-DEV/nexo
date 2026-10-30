-- V80: o aviso passa a sair do sistema.
--
-- Até aqui toda notificação morria dentro da aplicação: quem não abrisse o Nexo não
-- ficava sabendo de nada. Esta migration liga o canal de e-mail, e o faz com a mesma
-- divisão de responsabilidade que já rege o módulo — o administrador decide quais
-- avisos merecem a caixa de correio de alguém (coluna em regras_notificacao), o autor
-- decide quais informes precisam alcançar quem não vai entrar hoje (coluna em
-- comunicados) e cada pessoa decide de quê quer ser poupada (preferências).
--
-- Três coisas acontecem aqui, e a terceira é a que menos se espera de uma migration
-- sobre e-mail:
--
--   1) as colunas de canal e a trilha de envios (logs_email);
--   2) o gatilho que faltava — chamado.aberto —, sem o qual a equipe de suporte só
--      descobre um chamado novo se alguém pensar em abrir a fila;
--   3) a correção dos deeplinks de TODAS as regras de fábrica, que apontavam para
--      rotas inexistentes no frontend.
--
-- Sobre (3): as regras da V77/V79 foram semeadas com caminhos como /descontos/{id},
-- mas a rota real é /dp/descontos/:id. O Angular redireciona rota desconhecida para
-- 'home' em silêncio, então o defeito nunca apareceu como erro — o usuário clicava no
-- aviso e caía na tela inicial, o que se parece com lentidão, não com bug. Com o
-- e-mail o problema deixa de ser tolerável: o botão "Analisar e decidir" é a única
-- forma de voltar ao sistema a partir da caixa de correio.
--
-- Sobre o padrão LIGADO: o canal nasce ativo em todas as regras, como o sininho.
-- Quem não quer desliga — na regra (administrador) ou nas preferências (cada um).
-- Exigir que alguém ligue faria a funcionalidade parecer quebrada no dia seguinte.


-- ═══════════════════════════════════════════════════════════════════════════════
-- 1) Colunas de canal
-- ═══════════════════════════════════════════════════════════════════════════════

ALTER TABLE regras_notificacao
    ADD COLUMN enviar_email BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE comunicados
    ADD COLUMN notificar_por_email BOOLEAN NOT NULL DEFAULT TRUE;

-- MENCAO é vetada por domínio: marcação é conversa dentro da aplicação. O veto vive
-- em CategoriaNotificacao.permiteEmail() e o dado precisa concordar com ele.
UPDATE regras_notificacao SET enviar_email = FALSE WHERE categoria = 'MENCAO';

-- A coluna nasceu FALSE na V73 porque o canal não existia. Deixá-la assim silenciaria
-- por e-mail justamente quem já usou a tela de preferências — sem nunca ter tido a
-- opção de escolher sobre e-mail.
ALTER TABLE preferencias_notificacao ALTER COLUMN email SET DEFAULT TRUE;
UPDATE preferencias_notificacao SET email = TRUE  WHERE categoria <> 'MENCAO';
UPDATE preferencias_notificacao SET email = FALSE WHERE categoria =  'MENCAO';


-- ═══════════════════════════════════════════════════════════════════════════════
-- 2) Trilha de envios
-- ═══════════════════════════════════════════════════════════════════════════════
-- Responde à pergunta que log de aplicação responde mal, porque rotaciona e some:
-- "o aviso do desconto chegou para quem aprova?". Guarda o endereço, o provedor, o
-- identificador externo e o erro devolvido — o bastante para conferir no painel do
-- provedor, ou para concluir que o problema é a caixa da pessoa e não o sistema.
--
-- Não guarda o corpo da mensagem: a retentativa remonta o e-mail a partir da
-- notificação, que continua no banco. Assim a segunda tentativa leva os anexos que
-- existem agora, e a tabela não vira um espelho de tudo que já se escreveu.

CREATE TABLE logs_email (
    id UUID PRIMARY KEY,
    id_notificacao UUID NOT NULL,
    id_usuario UUID NOT NULL,
    -- 320 é o limite de um endereço de e-mail (64 local + @ + 255 de domínio).
    destinatario VARCHAR(320) NOT NULL,
    assunto VARCHAR(300) NOT NULL,
    -- Guardado para que a retentativa saiba a que módulo pedir os anexos.
    chave_gatilho VARCHAR(80),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    provedor VARCHAR(20),
    id_externo VARCHAR(120),
    tentativas INTEGER NOT NULL DEFAULT 0,
    ultimo_erro VARCHAR(1000),
    enviado_em TIMESTAMP WITH TIME ZONE,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_logs_email_status CHECK (status IN ('PENDENTE', 'ENVIADO', 'FALHOU', 'DESISTIU')),
    -- Um e-mail por notificação e destinatário. É o que impede o reprocessamento do
    -- outbox do Modulith de reenviar um aviso que já saiu.
    CONSTRAINT uk_logs_email_notificacao_usuario UNIQUE (id_notificacao, id_usuario)
);

-- A varredura de reenvio procura sempre a mesma coisa: o que falhou ou ficou pendente,
-- do mais antigo para o mais novo. O índice parcial deixa de fora as linhas ENVIADO,
-- que são a esmagadora maioria e nunca interessam à consulta.
CREATE INDEX idx_logs_email_reenvio
    ON logs_email (data_criacao)
    WHERE ativo = TRUE AND status IN ('PENDENTE', 'FALHOU');

-- Diagnóstico do dia a dia: "o que saiu para esta pessoa?".
CREATE INDEX idx_logs_email_usuario ON logs_email (id_usuario, data_criacao DESC);


-- ═══════════════════════════════════════════════════════════════════════════════
-- 3) Deeplinks: das rotas imaginadas para as rotas que existem
-- ═══════════════════════════════════════════════════════════════════════════════
-- Conferido contra ces-frontend/src/app/app.routes.ts.
--
-- O casamento por deeplink antigo (e não por nome da regra) é proposital: o nome é
-- editável pela tela e pode ter mudado; o caminho errado é o próprio defeito que se
-- está corrigindo, e só as regras que ainda o têm precisam do conserto.

UPDATE regras_notificacao SET deeplink_template = '/dp/descontos/{{idDesconto}}'
 WHERE deeplink_template = '/descontos/{{idDesconto}}';

UPDATE regras_notificacao SET deeplink_template = '/almoxarifado/solicitacoes/{{idSolicitacao}}'
 WHERE deeplink_template = '/estoque/solicitacoes/{{idSolicitacao}}';

-- Chamado tem duas telas para o mesmo registro, com permissões diferentes:
-- /chamados/meus/:id exige chamado:read (é a tela de quem abriu) e
-- /chamados/fila/:id exige chamado:manage (é a tela de quem atende). Mandar o
-- solicitante para a fila é levá-lo a uma tela que ele não tem permissão de ver.
UPDATE regras_notificacao SET deeplink_template = '/chamados/fila/{{idChamado}}'
 WHERE chave_gatilho = 'chamado.atribuido' AND deeplink_template = '/chamados/{{idChamado}}';

UPDATE regras_notificacao SET deeplink_template = '/chamados/meus/{{idChamado}}'
 WHERE chave_gatilho = 'chamado.resolvido' AND deeplink_template = '/chamados/{{idChamado}}';

UPDATE regras_notificacao SET deeplink_template = '/financeiro/compras/{{idSolicitacao}}'
 WHERE deeplink_template = '/financeiro/solicitacoes/{{idSolicitacao}}'
   AND id IN (SELECT id_regra FROM condicoes_regra_notificacao
               WHERE campo = 'tipo' AND valor = 'COMPRA' AND ativo = TRUE);

UPDATE regras_notificacao SET deeplink_template = '/financeiro/pagamentos/{{idSolicitacao}}'
 WHERE deeplink_template = '/financeiro/solicitacoes/{{idSolicitacao}}'
   AND id IN (SELECT id_regra FROM condicoes_regra_notificacao
               WHERE campo = 'tipo' AND valor = 'PAGAMENTO' AND ativo = TRUE);


-- ═══════════════════════════════════════════════════════════════════════════════
-- 4) Regras que atendiam dois públicos com um destino só
-- ═══════════════════════════════════════════════════════════════════════════════
-- Três regras da V79 endereçavam solicitante e responsável na mesma linha. Isso
-- funcionava quando o destino era o sininho, que abre a tela certa para cada um; com
-- um endereço absoluto no e-mail, um dos dois públicos recebe sempre o link errado.
--
-- A saída é a que a própria V79 já usa em 'desconto.validado': duas regras para o
-- mesmo gatilho, uma por público. Continuam sendo regras de sistema, desligáveis.

CREATE FUNCTION pg_temp.clonar_para_publico(
    p_regra_origem UUID, p_novo_nome VARCHAR, p_descricao VARCHAR,
    p_deeplink VARCHAR, p_papel VARCHAR)
RETURNS UUID AS $$
DECLARE
    v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        enviar_email, versao, data_criacao, usuario_criacao, ativo)
    SELECT v_id, chave_gatilho, p_novo_nome, p_descricao, TRUE, categoria, prioridade,
           titulo_template, corpo_template, p_deeplink, limiar, cooldown_minutos,
           enviar_email, 0, CURRENT_TIMESTAMP, 'SISTEMA', ativo
      FROM regras_notificacao WHERE id = p_regra_origem;

    INSERT INTO destinatarios_regra_notificacao (
        id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), v_id, 'PAPEL_DINAMICO', NULL, p_papel,
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

    -- O público migrou para a regra nova; deixá-lo também na antiga geraria dois
    -- e-mails do mesmo fato para a mesma pessoa.
    DELETE FROM destinatarios_regra_notificacao
     WHERE id_regra = p_regra_origem AND valor_alvo = p_papel AND tipo = 'PAPEL_DINAMICO';

    RETURN v_id;
END;
$$ LANGUAGE plpgsql;


DO $$
DECLARE
    r UUID;
BEGIN

    -- ── Nova mensagem: o solicitante fica na regra original, o atendimento sai ────
    SELECT id INTO r FROM regras_notificacao
     WHERE chave_gatilho = 'chamado.mensagem_recebida' AND sistema = TRUE
     ORDER BY data_criacao LIMIT 1;

    IF r IS NOT NULL THEN
        UPDATE regras_notificacao
           SET deeplink_template = '/chamados/meus/{{idChamado}}'
         WHERE id = r;

        PERFORM pg_temp.clonar_para_publico(r,
            'Nova mensagem em chamado — atendimento',
            'Avisa o responsável quando o solicitante escreve. Separada da regra do solicitante porque a tela de atendimento é outra.',
            '/chamados/fila/{{idChamado}}', 'responsavel');
    END IF;

    -- ── Reabertura: o responsável fica, o solicitante ganha regra própria ─────────
    SELECT id INTO r FROM regras_notificacao
     WHERE chave_gatilho = 'chamado.reaberto' AND sistema = TRUE
     ORDER BY data_criacao LIMIT 1;

    IF r IS NOT NULL THEN
        UPDATE regras_notificacao
           SET deeplink_template = '/chamados/fila/{{idChamado}}'
         WHERE id = r;

        PERFORM pg_temp.clonar_para_publico(r,
            'Chamado reaberto — aviso a quem abriu',
            'Confirma a quem abriu que o chamado voltou para atendimento.',
            '/chamados/meus/{{idChamado}}', 'solicitante');
    END IF;

END $$;


-- Financeiro: aprovada e rejeitada avisavam o solicitante sem distinguir compra de
-- pagamento — e as telas são distintas. Ganham condição sobre o tipo e uma gêmea.
CREATE FUNCTION pg_temp.separar_por_tipo(
    p_gatilho VARCHAR, p_nome_compra VARCHAR, p_nome_pagamento VARCHAR)
RETURNS VOID AS $$
DECLARE
    v_origem UUID;
    v_nova UUID;
BEGIN
    SELECT id INTO v_origem FROM regras_notificacao
     WHERE chave_gatilho = p_gatilho AND sistema = TRUE
       AND deeplink_template = '/financeiro/solicitacoes/{{idSolicitacao}}'
     ORDER BY data_criacao LIMIT 1;

    IF v_origem IS NULL THEN
        RETURN;
    END IF;

    v_nova := gen_random_uuid();
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        enviar_email, versao, data_criacao, usuario_criacao, ativo)
    SELECT v_nova, chave_gatilho, p_nome_pagamento, descricao, TRUE, categoria, prioridade,
           titulo_template, corpo_template, '/financeiro/pagamentos/{{idSolicitacao}}',
           limiar, cooldown_minutos, enviar_email, 0, CURRENT_TIMESTAMP, 'SISTEMA', ativo
      FROM regras_notificacao WHERE id = v_origem;

    INSERT INTO destinatarios_regra_notificacao (
        id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), v_nova, 'PAPEL_DINAMICO', NULL, 'solicitante',
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

    INSERT INTO condicoes_regra_notificacao (
        id, id_regra, campo, operador, valor, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), v_nova, 'tipo', 'IGUAL', 'PAGAMENTO',
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

    -- A original passa a responder só por compra.
    UPDATE regras_notificacao
       SET nome = p_nome_compra,
           deeplink_template = '/financeiro/compras/{{idSolicitacao}}'
     WHERE id = v_origem;

    INSERT INTO condicoes_regra_notificacao (
        id, id_regra, campo, operador, valor, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), v_origem, 'tipo', 'IGUAL', 'COMPRA',
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.separar_por_tipo('financeiro.solicitacao_aprovada',
    'Compra aprovada', 'Pagamento aprovado — aviso a quem pediu');

SELECT pg_temp.separar_por_tipo('financeiro.solicitacao_rejeitada',
    'Compra rejeitada', 'Pagamento rejeitado');


-- ═══════════════════════════════════════════════════════════════════════════════
-- 5) chamado.aberto — o gatilho que faltava
-- ═══════════════════════════════════════════════════════════════════════════════
-- O ciclo do chamado avisava sobre atribuição, mensagem, resolução e reabertura, mas
-- nada sobre a entrada na fila. O SLA de primeira resposta começa a correr na
-- abertura; sem aviso, ele corre contra um relógio que ninguém está olhando.
--
-- Duas regras, e as duas de propósito. A primeira endereça os membros cadastrados da
-- equipe do tópico — é o alvo certo, e o mais preciso. A segunda endereça
-- 'chamado:manage', e existe porque equipe sem membros cadastrados é estado normal
-- durante a implantação (o domínio permite, para aceitar atribuição livre): sem ela,
-- justamente nesse período o aviso não alcançaria ninguém. Quando as equipes
-- estiverem completas, basta desligar a segunda pela tela.

DO $$
DECLARE
    r UUID;
BEGIN

    r := gen_random_uuid();
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        enviar_email, versao, data_criacao, usuario_criacao, ativo)
    VALUES (r, 'chamado.aberto',
        'Chamado aberto — avisa a equipe',
        'Avisa os membros da equipe responsável assim que um chamado entra na fila, para que o SLA de primeira resposta não corra sem ninguém saber.',
        TRUE, 'CHAMADO', 'ALTA',
        'Novo chamado {{protocolo}} para {{equipe}}',
        '{{assunto}} — {{topico}}, prioridade {{prioridade}}.',
        '/chamados/fila/{{idChamado}}', NULL, 0,
        TRUE, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

    INSERT INTO destinatarios_regra_notificacao (
        id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), r, 'PAPEL_DINAMICO', NULL, 'equipeAtendimento',
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

    r := gen_random_uuid();
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        enviar_email, versao, data_criacao, usuario_criacao, ativo)
    VALUES (r, 'chamado.aberto',
        'Chamado aberto — rede de segurança da central',
        'Alcança quem administra a central de chamados. Existe para o período em que as equipes ainda não têm membros cadastrados; com as equipes completas, pode ser desligada.',
        TRUE, 'CHAMADO', 'NORMAL',
        'Novo chamado {{protocolo}} na fila',
        '{{assunto}} — {{topico}}, prioridade {{prioridade}}, equipe {{equipe}}.',
        '/chamados/fila/{{idChamado}}', NULL, 0,
        TRUE, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

    INSERT INTO destinatarios_regra_notificacao (
        id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), r, 'PERMISSAO', NULL, 'chamado:manage',
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

END $$;


-- ═══════════════════════════════════════════════════════════════════════════════
-- 6) Ruído previsível
-- ═══════════════════════════════════════════════════════════════════════════════
-- O canal nasce ligado em todas as regras, e para quase todas isso é certo: um
-- desconto, uma aprovação ou uma reabertura acontecem poucas vezes por registro.
--
-- 'chamado.mensagem_recebida' é a exceção conhecida — dispara a CADA resposta, e um
-- chamado com dez idas e vindas viraria dez e-mails para cada lado. Nasce com o
-- e-mail desligado; quem quiser liga na tela, com a informação de que isso significa
-- um e-mail por mensagem trocada.

UPDATE regras_notificacao SET enviar_email = FALSE
 WHERE chave_gatilho = 'chamado.mensagem_recebida';
