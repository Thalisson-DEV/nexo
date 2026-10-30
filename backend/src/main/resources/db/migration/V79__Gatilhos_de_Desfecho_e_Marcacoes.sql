-- V79: os desfechos passam a avisar, e nasce a marcação de pessoas.
--
-- Até aqui o sistema sabia dizer que HAVIA fila (gatilhos por limiar, V77), mas nunca
-- avisava a pessoa de que o SEU pedido teve resposta, nem convocava quem responde pelo
-- passo seguinte. Esta migration fecha as duas pontas de cada fluxo:
--
--   desconto:   solicitado -> quem valida | validado -> quem dá baixa | rejeitado/baixado -> quem pediu
--   financeiro: submetida  -> quem aprova | aprovada -> quem baixa    | rejeitada        -> quem pediu
--   estoque:    aprovada   -> quem separa | aprovada/rejeitada        -> quem pediu
--   chamado:    atribuído  -> o responsável | mensagem -> o outro lado | resolvido/reaberto
--
-- Ao contrário das regras da V77, estas nascem LIGADAS. As de limiar dependiam de um
-- número que só a organização sabe, e ligá-las com um palpite geraria ruído; estas são
-- todas por ocorrência e endereçam ou a pessoa diretamente envolvida no registro, ou
-- quem tem a atribuição do próximo passo. Não há como virarem spam: só disparam quando
-- algo de fato aconteceu com aquele registro específico.
--
-- Continuam sendo regras de sistema (sistema = TRUE): editáveis e desligáveis pela
-- tela, nunca removíveis.


-- ═══════════════════════════════════════════════════════════════════════════════
-- 1) Categoria MENCAO
-- ═══════════════════════════════════════════════════════════════════════════════
-- Marcação ganha categoria própria em vez de entrar em CHAMADO porque é o único
-- assunto endereçado à pessoa pelo nome. Sob CHAMADO, quem silenciasse a
-- movimentação da central perderia junto os avisos dirigidos a si — e o substrato
-- de marcações serve a outras origens além do chamado.

ALTER TABLE notificacoes DROP CONSTRAINT ck_notificacoes_categoria;
ALTER TABLE notificacoes ADD CONSTRAINT ck_notificacoes_categoria CHECK (categoria IN (
    'APROVACAO', 'SEGURANCA', 'OPERACIONAL', 'CHAMADO', 'MENCAO', 'COMUNICADO', 'SISTEMA'));

ALTER TABLE preferencias_notificacao DROP CONSTRAINT ck_preferencias_notificacao_categoria;
ALTER TABLE preferencias_notificacao ADD CONSTRAINT ck_preferencias_notificacao_categoria CHECK (categoria IN (
    'APROVACAO', 'SEGURANCA', 'OPERACIONAL', 'CHAMADO', 'MENCAO', 'COMUNICADO', 'SISTEMA'));

ALTER TABLE regras_notificacao DROP CONSTRAINT ck_regras_notificacao_categoria;
ALTER TABLE regras_notificacao ADD CONSTRAINT ck_regras_notificacao_categoria CHECK (categoria IN (
    'APROVACAO', 'SEGURANCA', 'OPERACIONAL', 'CHAMADO', 'MENCAO', 'COMUNICADO', 'SISTEMA'));


-- ═══════════════════════════════════════════════════════════════════════════════
-- 2) Marcações
-- ═══════════════════════════════════════════════════════════════════════════════
-- Uma linha por pessoa marcada, e não uma por texto com lista dentro, porque toda
-- pergunta útil aqui é sobre a pessoa ("onde fui marcado"). O agrupamento por texto
-- continua disponível por id_origem.

CREATE TABLE mencoes (
    id UUID PRIMARY KEY,
    origem VARCHAR(40) NOT NULL,
    -- Texto onde a marcação foi escrita (a interação do chamado).
    id_origem UUID NOT NULL,
    -- Registro a que o texto pertence (o chamado), para o link de volta.
    id_registro UUID NOT NULL,
    id_usuario_autor UUID NOT NULL,
    id_usuario_mencionado UUID NOT NULL,
    data_mencao TIMESTAMP WITH TIME ZONE NOT NULL,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    -- Uma origem nova exige código nos dois lados (o módulo que marca e o link de
    -- volta), então repetir a lista aqui não cria trabalho extra: cria um ponto de
    -- revisão para quando alguém acrescentar uma.
    CONSTRAINT ck_mencoes_origem CHECK (origem IN ('CHAMADO_INTERACAO')),
    CONSTRAINT ck_mencoes_autor CHECK (id_usuario_autor <> id_usuario_mencionado),
    -- Marcar a mesma pessoa duas vezes na mesma frase é um fato só.
    CONSTRAINT uk_mencoes_texto_usuario UNIQUE (origem, id_origem, id_usuario_mencionado)
);

CREATE INDEX idx_mencoes_usuario
    ON mencoes (id_usuario_mencionado, data_mencao DESC)
    WHERE ativo = TRUE;

CREATE INDEX idx_mencoes_registro
    ON mencoes (origem, id_registro)
    WHERE ativo = TRUE;


-- ═══════════════════════════════════════════════════════════════════════════════
-- 3) Regras de fábrica
-- ═══════════════════════════════════════════════════════════════════════════════
-- São dezessete regras com a mesma forma. As funções abaixo vivem em pg_temp — some
-- sozinhas ao fim da sessão, sem deixar rastro no schema — e existem para que cada
-- regra caiba em três linhas legíveis em vez de um bloco de INSERT repetido.
--
-- cooldown = 0 em todas: o escopo do silêncio é o registro de origem, e nenhum destes
-- gatilhos dispara duas vezes para o mesmo registro. Um cooldown aqui só teria o
-- efeito de engolir avisos legítimos.

CREATE FUNCTION pg_temp.nova_regra(
    p_gatilho VARCHAR, p_nome VARCHAR, p_descricao VARCHAR, p_categoria VARCHAR,
    p_prioridade VARCHAR, p_titulo VARCHAR, p_corpo VARCHAR, p_deeplink VARCHAR)
RETURNS UUID AS $$
DECLARE
    v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        versao, data_criacao, usuario_criacao, ativo)
    VALUES (v_id, p_gatilho, p_nome, p_descricao, TRUE, p_categoria, p_prioridade,
            p_titulo, p_corpo, p_deeplink, NULL, 0,
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);
    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.destinatario(p_regra UUID, p_tipo VARCHAR, p_valor VARCHAR)
RETURNS VOID AS $$
BEGIN
    INSERT INTO destinatarios_regra_notificacao (
        id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), p_regra, p_tipo, NULL, p_valor,
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.condicao(p_regra UUID, p_campo VARCHAR, p_operador VARCHAR, p_valor VARCHAR)
RETURNS VOID AS $$
BEGIN
    INSERT INTO condicoes_regra_notificacao (
        id, id_regra, campo, operador, valor, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), p_regra, p_campo, p_operador, p_valor,
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);
END;
$$ LANGUAGE plpgsql;


DO $$
DECLARE
    r UUID;
BEGIN

    -- ── Desconto ────────────────────────────────────────────────────────────────
    -- A cadeia completa: quem registra passa a bola para quem valida, que passa para
    -- quem dá baixa. Nenhum desses papéis está escrito no registro — todos são
    -- endereçados por permissão, então mudança de time não apodrece a regra.

    r := pg_temp.nova_regra('desconto.solicitado',
        'Desconto aguardando validação',
        'Avisa quem valida descontos assim que um termo é registrado, sem esperar a fila acumular.',
        'APROVACAO', 'NORMAL',
        'Novo desconto aguardando validação',
        'Um desconto por {{motivo}} em {{parcelas}}x foi registrado e aguarda sua validação.',
        '/descontos/{{idDesconto}}');
    PERFORM pg_temp.destinatario(r, 'PERMISSAO', 'desconto:validar');

    r := pg_temp.nova_regra('desconto.validado',
        'Desconto validado — pronto para baixa',
        'Convoca quem dá baixa nos descontos assim que a validação é concluída.',
        'APROVACAO', 'NORMAL',
        'Desconto validado aguardando baixa',
        'O desconto por {{motivo}} foi validado em R$ {{valorValidado}} e está pronto para baixa.',
        '/descontos/{{idDesconto}}');
    PERFORM pg_temp.destinatario(r, 'PERMISSAO', 'desconto:baixar');

    r := pg_temp.nova_regra('desconto.validado',
        'Confirmação de desconto validado',
        'Informa a quem registrou que o termo passou na validação e seguiu para baixa.',
        'APROVACAO', 'BAIXA',
        'Seu desconto foi validado',
        'O desconto por {{motivo}} foi validado em R$ {{valorValidado}} e seguiu para a baixa.',
        '/descontos/{{idDesconto}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'solicitante');

    -- Prioridade ALTA: é o único desfecho que exige ação de quem recebe.
    r := pg_temp.nova_regra('desconto.rejeitado',
        'Desconto rejeitado',
        'Devolve a recusa e o motivo a quem registrou o termo.',
        'APROVACAO', 'ALTA',
        'Seu desconto foi rejeitado',
        'O desconto por {{motivo}} foi rejeitado. Motivo: {{motivoRejeicao}}',
        '/descontos/{{idDesconto}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'solicitante');

    r := pg_temp.nova_regra('desconto.baixado',
        'Desconto baixado',
        'Fecha o ciclo com quem registrou o termo.',
        'APROVACAO', 'BAIXA',
        'Desconto baixado',
        'A baixa do desconto por {{motivo}} foi registrada em R$ {{valorBaixado}}.',
        '/descontos/{{idDesconto}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'solicitante');


    -- ── Chamados ────────────────────────────────────────────────────────────────

    r := pg_temp.nova_regra('chamado.atribuido',
        'Chamado atribuído a você',
        'Avisa quem passou a responder pelo chamado. Sem isso, a atribuição só chega à pessoa se ela reparar sozinha.',
        'CHAMADO', 'NORMAL',
        'O chamado {{protocolo}} é seu',
        'Você é o responsável pelo chamado {{protocolo}} – {{assunto}} (prioridade {{prioridade}}).',
        '/chamados/{{idChamado}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'responsavel');

    -- Uma regra só para os dois lados: o evento nunca preenche o papel de quem
    -- escreveu, então quem recebe é sempre o outro lado da conversa.
    r := pg_temp.nova_regra('chamado.mensagem_recebida',
        'Nova mensagem em chamado',
        'Avisa o outro lado da conversa a cada mensagem pública — o solicitante quando o atendimento responde, e vice-versa.',
        'CHAMADO', 'NORMAL',
        'Nova mensagem no chamado {{protocolo}}',
        '{{autor}}: {{trecho}}',
        '/chamados/{{idChamado}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'solicitante');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'responsavel');

    r := pg_temp.nova_regra('chamado.resolvido',
        'Chamado resolvido',
        'Avisa quem abriu, com a solução e o prazo que ainda resta para reabrir.',
        'CHAMADO', 'NORMAL',
        'Chamado {{protocolo}} resolvido',
        'Solução: {{solucao}} — você tem {{prazoReaberturaHoras}}h para reabrir caso não tenha resolvido.',
        '/chamados/{{idChamado}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'solicitante');

    r := pg_temp.nova_regra('chamado.reaberto',
        'Chamado reaberto',
        'Avisa quem já tinha o caso por encerrado. Chamado sem responsável no momento da reabertura não alcança ninguém — nesse caso, acrescente um destinatário por permissão.',
        'CHAMADO', 'ALTA',
        'Chamado {{protocolo}} foi reaberto',
        'O chamado {{protocolo}} – {{assunto}} voltou para atendimento.',
        '/chamados/{{idChamado}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'responsavel');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'solicitante');


    -- ── Financeiro ──────────────────────────────────────────────────────────────
    -- Compra e pagamento compartilham o gatilho e se separam por condição sobre o
    -- tipo, porque quem aprova cada um é gente diferente.

    r := pg_temp.nova_regra('financeiro.solicitacao_submetida',
        'Compra submetida para aprovação',
        'Avisa quem aprova compras no momento da submissão, sem esperar a fila acumular.',
        'APROVACAO', 'NORMAL',
        'Compra nº {{numero}} aguardando aprovação',
        '{{descricao}} — R$ {{valor}} (prioridade {{prioridade}}).',
        '/financeiro/solicitacoes/{{idSolicitacao}}');
    PERFORM pg_temp.condicao(r, 'tipo', 'IGUAL', 'COMPRA');
    PERFORM pg_temp.destinatario(r, 'PERMISSAO', 'financeiro:compra:aprovar');

    r := pg_temp.nova_regra('financeiro.solicitacao_submetida',
        'Pagamento submetido para aprovação',
        'Avisa quem aprova pagamentos no momento da submissão.',
        'APROVACAO', 'NORMAL',
        'Pagamento nº {{numero}} aguardando aprovação',
        '{{descricao}} — R$ {{valor}} (prioridade {{prioridade}}).',
        '/financeiro/solicitacoes/{{idSolicitacao}}');
    PERFORM pg_temp.condicao(r, 'tipo', 'IGUAL', 'PAGAMENTO');
    PERFORM pg_temp.destinatario(r, 'PERMISSAO', 'financeiro:pagamento:aprovar');

    r := pg_temp.nova_regra('financeiro.solicitacao_aprovada',
        'Solicitação financeira aprovada',
        'Confirma a autorização a quem pediu, com o valor efetivamente aprovado — que pode ser menor que o solicitado.',
        'APROVACAO', 'NORMAL',
        'Sua solicitação nº {{numero}} foi aprovada',
        '{{descricao}} — autorizado R$ {{valorAprovado}}. {{observacao}}',
        '/financeiro/solicitacoes/{{idSolicitacao}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'solicitante');

    -- Aprovar um pagamento não o conclui: alguém ainda precisa baixar as parcelas.
    r := pg_temp.nova_regra('financeiro.solicitacao_aprovada',
        'Pagamento aprovado — pronto para baixa',
        'Convoca quem liquida as parcelas assim que um pagamento é autorizado.',
        'APROVACAO', 'NORMAL',
        'Pagamento nº {{numero}} aprovado e pronto para baixa',
        '{{descricao}} — R$ {{valorAprovado}} autorizados, aguardando liquidação.',
        '/financeiro/solicitacoes/{{idSolicitacao}}');
    PERFORM pg_temp.condicao(r, 'tipo', 'IGUAL', 'PAGAMENTO');
    PERFORM pg_temp.destinatario(r, 'PERMISSAO', 'financeiro:pagamento:baixar');

    r := pg_temp.nova_regra('financeiro.solicitacao_rejeitada',
        'Solicitação financeira rejeitada',
        'Devolve a recusa e a justificativa a quem pediu, para que a próxima tentativa não repita o mesmo defeito.',
        'APROVACAO', 'ALTA',
        'Sua solicitação nº {{numero}} foi rejeitada',
        '{{descricao}} — motivo: {{motivoRejeicao}}',
        '/financeiro/solicitacoes/{{idSolicitacao}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'solicitante');


    -- ── Estoque ─────────────────────────────────────────────────────────────────

    r := pg_temp.nova_regra('estoque.material_aprovado',
        'Requisição de material aprovada',
        'Confirma a liberação a quem requisitou.',
        'OPERACIONAL', 'NORMAL',
        'Sua requisição de material foi aprovada',
        '{{totalItens}} item(ns) liberado(s) de {{estoqueOrigem}} para {{estoqueDestino}}.',
        '/estoque/solicitacoes/{{idSolicitacao}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'requisitante');

    r := pg_temp.nova_regra('estoque.material_aprovado',
        'Requisição liberada para separação',
        'Avisa o almoxarifado de que há material a separar.',
        'OPERACIONAL', 'NORMAL',
        'Requisição liberada para separação',
        '{{totalItens}} item(ns) a separar em {{estoqueOrigem}}, destino {{estoqueDestino}}.',
        '/estoque/solicitacoes/{{idSolicitacao}}');
    PERFORM pg_temp.destinatario(r, 'PERMISSAO', 'estoque:movimentacao:write');

    r := pg_temp.nova_regra('estoque.material_rejeitado',
        'Requisição de material rejeitada',
        'Devolve motivo e justificativa a quem requisitou.',
        'OPERACIONAL', 'ALTA',
        'Sua requisição de material foi rejeitada',
        'Motivo: {{motivoRejeicao}}. {{justificativa}}',
        '/estoque/solicitacoes/{{idSolicitacao}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'requisitante');


    -- ── Marcações ───────────────────────────────────────────────────────────────
    -- O destino é {{link}}, e não um caminho fixo: a marcação nasce de origens
    -- diferentes e cada uma sabe para onde levar. Assim a regra continua correta
    -- quando outras telas passarem a aceitar @.

    r := pg_temp.nova_regra('mencao.usuario_marcado',
        'Você foi marcado',
        'Avisa quem foi marcado com @ em uma mensagem. Prioridade alta porque a marcação é dirigida à pessoa pelo nome e normalmente pede uma resposta.',
        'MENCAO', 'ALTA',
        '{{autor}} marcou você',
        'Em {{origem}}: {{trecho}}',
        '{{link}}');
    PERFORM pg_temp.destinatario(r, 'PAPEL_DINAMICO', 'mencionado');

END $$;
