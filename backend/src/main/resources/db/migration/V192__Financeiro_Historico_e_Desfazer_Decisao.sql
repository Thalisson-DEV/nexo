-- V192: histórico das solicitações financeiras e o "desfazer decisão".
--
-- Até aqui a solicitação guardava só a ÚLTIMA decisão, em colunas próprias. Isso
-- bastava enquanto aprovar e rejeitar eram definitivos. Com a possibilidade de
-- devolver uma solicitação aprovada (ou rejeitada) à fila de decisão, as colunas
-- passam a ser apagadas, e a trilha precisa viver em outro lugar: uma linha por
-- transição, imutável, com quem fez, quando, por quanto e por quê.


-- ═══════════════════════════════════════════════════════════════════════════════
-- 1) Histórico
-- ═══════════════════════════════════════════════════════════════════════════════
-- Compra e pagamento compartilham a tabela porque percorrem o mesmo ciclo; o tipo
-- fica na linha para que a consulta por solicitação não dependa de saber de qual
-- das duas tabelas o id veio.

CREATE TABLE financeiro_solicitacao_evento (
    id UUID PRIMARY KEY,
    id_solicitacao UUID NOT NULL,
    tipo_solicitacao VARCHAR(20) NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    status_anterior VARCHAR(20),
    status_novo VARCHAR(20) NOT NULL,
    id_usuario UUID,
    valor NUMERIC(14, 2),
    texto TEXT,
    ocorrido_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_fin_evento_tipo_solicitacao CHECK (tipo_solicitacao IN ('COMPRA', 'PAGAMENTO')),
    CONSTRAINT ck_fin_evento_tipo CHECK (tipo IN (
        'CRIADA', 'SUBMETIDA', 'APROVADA', 'REJEITADA', 'DECISAO_DESFEITA', 'CANCELADA'))
);

CREATE INDEX idx_fin_evento_solicitacao
    ON financeiro_solicitacao_evento (id_solicitacao, ocorrido_em);


-- O passado é reconstruído a partir das colunas: é tudo o que se sabe dele, e é
-- exatamente o que a tela de detalhe já mostrava.
CREATE FUNCTION pg_temp.reconstruir_historico(p_tabela VARCHAR, p_tipo VARCHAR)
RETURNS VOID AS $$
BEGIN
    EXECUTE format($sql$
        INSERT INTO financeiro_solicitacao_evento
            (id, id_solicitacao, tipo_solicitacao, tipo, status_anterior, status_novo,
             id_usuario, valor, texto, ocorrido_em)
        SELECT gen_random_uuid(), s.id, %L, e.tipo, e.anterior, e.novo, e.usuario, e.valor, e.texto, e.quando
          FROM %I s
         CROSS JOIN LATERAL (VALUES
               ('CRIADA', NULL, 'RASCUNHO', s.usuario_solicitante_id, s.valor, NULL::TEXT, s.data_criacao),
               ('SUBMETIDA', 'RASCUNHO', 'SOLICITADO', s.usuario_solicitante_id, s.valor, NULL, s.data_submissao),
               ('APROVADA', 'SOLICITADO', 'APROVADO', s.usuario_decisao_id, s.valor_aprovado, s.observacao_decisao,
                   CASE WHEN s.status = 'APROVADO' THEN s.data_decisao END),
               ('REJEITADA', 'SOLICITADO', 'REJEITADO', s.usuario_decisao_id, NULL, s.motivo_rejeicao,
                   CASE WHEN s.status = 'REJEITADO' THEN s.data_decisao END),
               ('CANCELADA', CASE WHEN s.data_submissao IS NULL THEN 'RASCUNHO' ELSE 'SOLICITADO' END,
                   'CANCELADO', s.usuario_cancelamento_id, NULL, s.motivo_cancelamento,
                   CASE WHEN s.status = 'CANCELADO' THEN s.data_cancelamento END)
              ) AS e(tipo, anterior, novo, usuario, valor, texto, quando)
         WHERE e.quando IS NOT NULL
    $sql$, p_tipo, p_tabela);
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.reconstruir_historico('financeiro_solicitacao_compra', 'COMPRA');
SELECT pg_temp.reconstruir_historico('financeiro_solicitacao_pagamento', 'PAGAMENTO');


-- ═══════════════════════════════════════════════════════════════════════════════
-- 2) Aviso de decisão desfeita
-- ═══════════════════════════════════════════════════════════════════════════════
-- Nasce LIGADA, ao contrário das demais regras do financeiro: devolver à fila uma
-- decisão já comunicada muda o que a pessoa acredita sobre o próprio pedido, e
-- quem desfaz conta com esse aviso. Os papéis excluem quem desfez.

CREATE FUNCTION pg_temp.regra_decisao_desfeita(p_tipo VARCHAR, p_nome VARCHAR, p_caminho VARCHAR)
RETURNS VOID AS $$
DECLARE
    v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO regras_notificacao (
        id, chave_gatilho, nome, descricao, sistema, categoria, prioridade,
        titulo_template, corpo_template, deeplink_template, limiar, cooldown_minutos,
        versao, data_criacao, usuario_criacao, ativo)
    VALUES (v_id, 'financeiro.decisao_desfeita', p_nome,
            'Avisa quem pediu e quem tinha decidido que a solicitação voltou a aguardar aprovação.',
            TRUE, 'APROVACAO', 'ALTA',
            'Solicitação nº {{numero}} voltou para aprovação',
            '{{decisaoAnterior}} desfeita. Motivo: {{motivo}}',
            p_caminho || '/{{idSolicitacao}}', NULL, 0,
            0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

    INSERT INTO condicoes_regra_notificacao (
        id, id_regra, campo, operador, valor, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), v_id, 'tipo', 'IGUAL', p_tipo, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);

    INSERT INTO destinatarios_regra_notificacao (
        id, id_regra, tipo, id_alvo, valor_alvo, versao, data_criacao, usuario_criacao, ativo)
    VALUES (gen_random_uuid(), v_id, 'PAPEL_DINAMICO', NULL, 'solicitante', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
           (gen_random_uuid(), v_id, 'PAPEL_DINAMICO', NULL, 'decisorAnterior', 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE);
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.regra_decisao_desfeita('COMPRA', 'Compra devolvida para aprovação', '/financeiro/compras');
SELECT pg_temp.regra_decisao_desfeita('PAGAMENTO', 'Pagamento devolvido para aprovação', '/financeiro/pagamentos');
