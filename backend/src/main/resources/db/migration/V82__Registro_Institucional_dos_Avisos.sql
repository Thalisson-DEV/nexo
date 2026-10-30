-- V82: os avisos passam a falar no registro de um sistema de gestão.
--
-- Os textos de fábrica das V77/V79/V80 foram escritos aviso a aviso, e o resultado é
-- um conjunto sem registro comum: convivem "O chamado CH-0042 é seu" e "Pagamento nº
-- 118 aprovado e pronto para baixa". A primeira forma é de conversa; a segunda, de
-- sistema. Misturadas, tiram a autoridade do canal — e um aviso que não parece oficial
-- é tratado como opcional.
--
-- A regra aplicada aqui é uma só: TÍTULO nomeia o objeto e o estado em que ele entrou
-- ("Solicitação nº 118 rejeitada"), CORPO informa o dado que sustenta esse estado e o
-- que se espera de quem lê. Some o possessivo de vitrine ("Sua solicitação foi
-- aprovada!"), que é a forma como uma loja fala com um cliente, não como uma empresa
-- registra um ato.
--
-- ─────────────────────────────────────────────────────────────────────────────
-- Por que cada UPDATE casa com o texto antigo inteiro
-- ─────────────────────────────────────────────────────────────────────────────
-- Estes templates são DADO editável: a tela de regras grava titulo_template e
-- corpo_template (PUT /v1/notificacoes/regras/{id}). Um UPDATE por chave_gatilho
-- apagaria, sem aviso, o texto que um administrador ajustou para a realidade dele —
-- e a migration não tem como devolver.
--
-- Casando pelo par (titulo, corpo) de fábrica, o comando só alcança o que ninguém
-- tocou. Quem personalizou permanece com a sua versão, que é a decisão mais recente
-- e mais informada sobre aquele aviso. É o mesmo critério que a V80 usou ao corrigir
-- deeplinks pelo caminho errado em vez de pelo nome da regra.
--
-- MENCAO fica de fora de propósito: marcação nunca sai por e-mail (veto em
-- CategoriaNotificacao.permiteEmail) e vive só no sininho, onde "{{autor}} marcou
-- você" é exatamente o registro certo.


CREATE FUNCTION pg_temp.reescrever(
    p_titulo_antigo VARCHAR, p_corpo_antigo VARCHAR,
    p_titulo_novo   VARCHAR, p_corpo_novo   VARCHAR)
RETURNS VOID AS $$
BEGIN
    UPDATE regras_notificacao
       SET titulo_template          = p_titulo_novo,
           corpo_template           = p_corpo_novo,
           data_ultima_alteracao    = CURRENT_TIMESTAMP,
           usuario_ultima_alteracao = 'SISTEMA'
     WHERE sistema = TRUE
       AND titulo_template = p_titulo_antigo
       AND corpo_template  = p_corpo_antigo;
END;
$$ LANGUAGE plpgsql;


-- ═══════════════════════════════════════════════════════════════════════════════
-- Avisos por limiar (V77)
-- ═══════════════════════════════════════════════════════════════════════════════
-- "Há N desvio(s)..." vira "N desvio(s) permanecem...": o verbo carrega a informação
-- que importa, que é a permanência da fila, não a existência dela. E todos passam a
-- citar o limite configurado — sem ele, quem recebe não sabe se o número é alto.

SELECT pg_temp.reescrever(
    'Descontos aguardando sua aprovação',
    'Há {{total}} desconto(s) solicitado(s) aguardando análise.',
    'Descontos pendentes de aprovação',
    '{{total}} desconto(s) aguardam análise, acima do limite de {{limiar}} definido para este aviso.');

SELECT pg_temp.reescrever(
    'Desvios de segurança exigem atenção',
    'Há {{total}} desvio(s) de segurança sem resolução, acima do limite de {{limiar}}.',
    'Desvios de segurança sem resolução',
    '{{total}} desvio(s) de segurança permanecem sem resolução, acima do limite de {{limiar}} definido para este aviso.');

SELECT pg_temp.reescrever(
    'Solicitações financeiras aguardando decisão',
    'Há {{total}} solicitação(ões) de compra ou pagamento aguardando aprovação.',
    'Solicitações financeiras pendentes de decisão',
    '{{total}} solicitação(ões) de compra ou pagamento aguardam aprovação, acima do limite de {{limiar}} definido para este aviso.');

SELECT pg_temp.reescrever(
    'Chamados aguardando atendimento',
    'Há {{total}} chamado(s) na fila, acima do limite de {{limiar}}.',
    'Chamados aguardando atendimento',
    '{{total}} chamado(s) permanecem na fila, acima do limite de {{limiar}} definido para este aviso.');


-- ═══════════════════════════════════════════════════════════════════════════════
-- Descontos
-- ═══════════════════════════════════════════════════════════════════════════════
-- Nos desfechos, o motivo da recusa ganha parágrafo próprio. O montador quebra o
-- corpo por linha, então a quebra vira separação visível — e o motivo de uma rejeição
-- é justamente o que a pessoa procura ao abrir o aviso.

SELECT pg_temp.reescrever(
    'Desconto registrado',
    'Seu desconto por {{motivo}} em {{parcelas}}x foi registrado e aguarda validação.',
    'Desconto registrado e enviado para validação',
    'O desconto por {{motivo}} em {{parcelas}}x foi registrado e segue para validação. Você será avisado quando houver decisão.');

SELECT pg_temp.reescrever(
    'Novo desconto aguardando validação',
    'Um desconto por {{motivo}} em {{parcelas}}x foi registrado e aguarda sua validação.',
    'Desconto aguardando validação',
    'Um desconto por {{motivo}} em {{parcelas}}x foi registrado e aguarda validação.');

SELECT pg_temp.reescrever(
    'Desconto validado aguardando baixa',
    'O desconto por {{motivo}} foi validado em R$ {{valorValidado}} e está pronto para baixa.',
    'Desconto validado, aguardando baixa',
    'O desconto por {{motivo}} foi validado em R$ {{valorValidado}} e aguarda o registro da baixa.');

SELECT pg_temp.reescrever(
    'Seu desconto foi validado',
    'O desconto por {{motivo}} foi validado em R$ {{valorValidado}} e seguiu para a baixa.',
    'Desconto validado',
    'O desconto por {{motivo}} foi validado em R$ {{valorValidado}} e seguiu para baixa.');

SELECT pg_temp.reescrever(
    'Seu desconto foi rejeitado',
    'O desconto por {{motivo}} foi rejeitado. Motivo: {{motivoRejeicao}}',
    'Desconto rejeitado',
    E'O desconto por {{motivo}} foi rejeitado.\nMotivo informado: {{motivoRejeicao}}');

SELECT pg_temp.reescrever(
    'Desconto baixado',
    'A baixa do desconto por {{motivo}} foi registrada em R$ {{valorBaixado}}.',
    'Desconto baixado',
    'A baixa do desconto por {{motivo}} foi registrada em R$ {{valorBaixado}}. O ciclo está encerrado.');


-- ═══════════════════════════════════════════════════════════════════════════════
-- Chamados
-- ═══════════════════════════════════════════════════════════════════════════════
-- "O chamado {{protocolo}} é seu" era o caso mais grave do conjunto: informal e
-- ambíguo — pode ser lido como "você abriu" em vez de "você responde por ele".
--
-- A prioridade sai dos corpos porque agora aparece duas vezes sozinha: na linha de
-- identificação do e-mail e na ficha de dados. Repeti-la na frase era ruído.

SELECT pg_temp.reescrever(
    'O chamado {{protocolo}} é seu',
    'Você é o responsável pelo chamado {{protocolo}} – {{assunto}} (prioridade {{prioridade}}).',
    'Chamado {{protocolo}} atribuído a você',
    'Você passou a responder pelo chamado {{protocolo}} — {{assunto}}.');

SELECT pg_temp.reescrever(
    'Nova mensagem no chamado {{protocolo}}',
    '{{autor}}: {{trecho}}',
    'Nova mensagem no chamado {{protocolo}}',
    E'{{autor}} escreveu:\n{{trecho}}');

SELECT pg_temp.reescrever(
    'Chamado {{protocolo}} resolvido',
    'Solução: {{solucao}} — você tem {{prazoReaberturaHoras}}h para reabrir caso não tenha resolvido.',
    'Chamado {{protocolo}} resolvido',
    E'Solução registrada: {{solucao}}\nCaso o problema persista, a reabertura fica disponível por {{prazoReaberturaHoras}} horas.');

SELECT pg_temp.reescrever(
    'Chamado {{protocolo}} foi reaberto',
    'O chamado {{protocolo}} – {{assunto}} voltou para atendimento.',
    'Chamado {{protocolo}} reaberto',
    'O chamado {{protocolo}} — {{assunto}} retornou para atendimento.');

SELECT pg_temp.reescrever(
    'Novo chamado {{protocolo}} para {{equipe}}',
    '{{assunto}} — {{topico}}, prioridade {{prioridade}}.',
    'Chamado {{protocolo}} aberto para {{equipe}}',
    E'{{assunto}}\nTópico {{topico}}. O prazo de primeira resposta começa a contar agora.');

SELECT pg_temp.reescrever(
    'Novo chamado {{protocolo}} na fila',
    '{{assunto}} — {{topico}}, prioridade {{prioridade}}, equipe {{equipe}}.',
    'Chamado {{protocolo}} aberto',
    E'{{assunto}}\nTópico {{topico}}, equipe {{equipe}}.');


-- ═══════════════════════════════════════════════════════════════════════════════
-- Financeiro
-- ═══════════════════════════════════════════════════════════════════════════════
-- O valor sai do meio da frase e vira linha própria e rotulada. Num aviso sobre
-- dinheiro, o número é o que se procura primeiro — e "R$ 12.480,00" no fim de um
-- travessão se perde.

SELECT pg_temp.reescrever(
    'Compra nº {{numero}} aguardando aprovação',
    '{{descricao}} — R$ {{valor}} (prioridade {{prioridade}}).',
    'Compra nº {{numero}} aguardando aprovação',
    E'{{descricao}}\nValor solicitado: R$ {{valor}}.');

SELECT pg_temp.reescrever(
    'Pagamento nº {{numero}} aguardando aprovação',
    '{{descricao}} — R$ {{valor}} (prioridade {{prioridade}}).',
    'Pagamento nº {{numero}} aguardando aprovação',
    E'{{descricao}}\nValor solicitado: R$ {{valor}}.');

SELECT pg_temp.reescrever(
    'Sua solicitação nº {{numero}} foi aprovada',
    '{{descricao}} — autorizado R$ {{valorAprovado}}. {{observacao}}',
    'Solicitação nº {{numero}} aprovada',
    E'{{descricao}}\nValor autorizado: R$ {{valorAprovado}}. {{observacao}}');

SELECT pg_temp.reescrever(
    'Pagamento nº {{numero}} aprovado e pronto para baixa',
    '{{descricao}} — R$ {{valorAprovado}} autorizados, aguardando liquidação.',
    'Pagamento nº {{numero}} aprovado, aguardando liquidação',
    E'{{descricao}}\nValor autorizado: R$ {{valorAprovado}}.');

SELECT pg_temp.reescrever(
    'Sua solicitação nº {{numero}} foi rejeitada',
    '{{descricao}} — motivo: {{motivoRejeicao}}',
    'Solicitação nº {{numero}} rejeitada',
    E'{{descricao}}\nMotivo informado: {{motivoRejeicao}}');


-- ═══════════════════════════════════════════════════════════════════════════════
-- Estoque
-- ═══════════════════════════════════════════════════════════════════════════════

SELECT pg_temp.reescrever(
    'Sua requisição de material foi aprovada',
    '{{totalItens}} item(ns) liberado(s) de {{estoqueOrigem}} para {{estoqueDestino}}.',
    'Requisição de material aprovada',
    '{{totalItens}} item(ns) liberado(s) de {{estoqueOrigem}} para {{estoqueDestino}}.');

SELECT pg_temp.reescrever(
    'Requisição liberada para separação',
    '{{totalItens}} item(ns) a separar em {{estoqueOrigem}}, destino {{estoqueDestino}}.',
    'Requisição liberada para separação',
    '{{totalItens}} item(ns) a separar em {{estoqueOrigem}}, com destino a {{estoqueDestino}}.');

SELECT pg_temp.reescrever(
    'Sua requisição de material foi rejeitada',
    'Motivo: {{motivoRejeicao}}. {{justificativa}}',
    'Requisição de material rejeitada',
    E'A requisição foi rejeitada.\nMotivo informado: {{motivoRejeicao}}. {{justificativa}}');
