-- Consolida o campo legado quantidade_entregue nas projecoes bruta/estornada
-- usadas pelo fluxo tecnico. A quantidade liquida permanece equivalente ao
-- estado que o comercial apresentava antes desta migracao.
WITH projecao AS (
    SELECT
        item.id,
        solicitacao.status,
        COALESCE(item.quantidade_entregue, 0) AS liquida_legada,
        COALESCE(item.quantidade_entregue_bruta, 0) AS bruta_atual,
        COALESCE(item.quantidade_entrega_estornada, 0) AS estornada_atual
    FROM itens_solicitacao item
    JOIN solicitacoes_material solicitacao ON solicitacao.id = item.id_solicitacao
    WHERE solicitacao.contexto = 'COMERCIAL'
), normalizada AS (
    SELECT
        id,
        status,
        CASE
            WHEN status = 'ESTORNADO'
                THEN GREATEST(bruta_atual, estornada_atual, liquida_legada)
            ELSE GREATEST(bruta_atual, liquida_legada + estornada_atual)
        END AS nova_bruta,
        liquida_legada,
        estornada_atual
    FROM projecao
)
UPDATE itens_solicitacao item
SET quantidade_entregue_bruta = normalizada.nova_bruta,
    quantidade_entrega_estornada = CASE
        WHEN normalizada.status = 'ESTORNADO' THEN normalizada.nova_bruta
        ELSE GREATEST(
            normalizada.estornada_atual,
            normalizada.nova_bruta - normalizada.liquida_legada)
    END,
    quantidade_entregue = CASE
        WHEN normalizada.status = 'ESTORNADO' THEN 0
        ELSE normalizada.liquida_legada
    END
FROM normalizada
WHERE item.id = normalizada.id;
