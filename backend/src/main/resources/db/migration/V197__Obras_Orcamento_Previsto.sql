-- =====================================================================
-- V197: Serviços previstos do orçamento da obra (importados)
--
-- Base de comparação com o planejamento na aba de orçamento. Reimportar o
-- orçamento de uma obra desativa os itens anteriores (soft delete) e grava os
-- novos, ligados à importação que os trouxe.
-- =====================================================================

CREATE TABLE itens_orcamento_obras (
    id UUID PRIMARY KEY,
    id_obra UUID NOT NULL REFERENCES obras (id),
    id_importacao UUID REFERENCES importacoes_obras (id),
    codigo VARCHAR(60) NOT NULL,
    descricao VARCHAR(300),
    unidade VARCHAR(20),
    quantidade NUMERIC(14, 4) NOT NULL,
    valor_unitario NUMERIC(15, 4),
    valor_total NUMERIC(15, 2) NOT NULL,
    id_acao UUID,
    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_itens_orcamento_obras_obra ON itens_orcamento_obras (id_obra) WHERE ativo = TRUE;
