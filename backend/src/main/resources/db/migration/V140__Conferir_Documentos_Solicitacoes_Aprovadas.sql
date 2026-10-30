-- V140: Declara como conferidos todos os documentos ativos vinculados a
-- solicitacoes de compra e pagamento que ja foram aprovadas.

-- Compras aprovadas
UPDATE financeiro_documento d
SET situacao = 'CONFERIDO',
    usuario_conferencia_id = COALESCE(c.usuario_decisao_id, d.usuario_envio_id),
    data_conferencia = COALESCE(c.data_decisao, d.data_criacao, CURRENT_TIMESTAMP),
    observacao_conferencia = 'Conferência automática por aprovação da solicitação.',
    motivo_pendencia = NULL
FROM financeiro_solicitacao_compra c
WHERE d.origem_solicitacao_id = c.id
  AND c.status = 'APROVADO'
  AND d.ativo = TRUE
  AND d.situacao IN ('PENDENTE', 'COM_PENDENCIA');

-- Pagamentos aprovados
UPDATE financeiro_documento d
SET situacao = 'CONFERIDO',
    usuario_conferencia_id = COALESCE(p.usuario_decisao_id, d.usuario_envio_id),
    data_conferencia = COALESCE(p.data_decisao, d.data_criacao, CURRENT_TIMESTAMP),
    observacao_conferencia = 'Conferência automática por aprovação da solicitação.',
    motivo_pendencia = NULL
FROM financeiro_solicitacao_pagamento p
WHERE d.origem_solicitacao_id = p.id
  AND p.status = 'APROVADO'
  AND d.ativo = TRUE
  AND d.situacao IN ('PENDENTE', 'COM_PENDENCIA');
