-- =====================================================================
-- V190: Reserva de números de série para a solicitação comercial
--
-- O almoxarifado define as séries de um material antes da entrega e,
-- na entrega, só confirma. Enquanto a reserva vale, a série não sai do
-- estoque por outro caminho (outra entrega, transferência, baixa).
--
-- A reserva fica numa tabela própria, e não em saldos_seriais, para não
-- mexer na data de alteração da série (base do "parado há N dias").
-- Ela só vale enquanto o item ainda tem saldo a entregar e a solicitação
-- aceita entrega; rejeitar, cancelar ou encerrar libera sem tocar aqui.
-- =====================================================================

CREATE TABLE reservas_serie_solicitacao (
    id UUID PRIMARY KEY,
    id_saldo_serie UUID NOT NULL REFERENCES saldos_seriais (id),
    -- Item removido da solicitação (ex.: desmembrado na entrega) leva as reservas junto.
    id_item_solicitacao UUID NOT NULL REFERENCES itens_solicitacao (id) ON DELETE CASCADE,
    id_usuario UUID NOT NULL,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

-- Uma série tem no máximo uma reserva registrada por vez.
CREATE UNIQUE INDEX ux_reservas_serie_solicitacao_serie_ativa
    ON reservas_serie_solicitacao (id_saldo_serie) WHERE ativo;

CREATE INDEX ix_reservas_serie_solicitacao_item
    ON reservas_serie_solicitacao (id_item_solicitacao) WHERE ativo;
