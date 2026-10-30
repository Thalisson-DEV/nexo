-- Eventos de baseline comercial nao possuem obra tecnica.
ALTER TABLE operacoes_solicitacao_material
    ALTER COLUMN id_agrupador SET NOT NULL;

ALTER TABLE eventos_solicitacao_material
    ALTER COLUMN id_agrupador DROP NOT NULL;

ALTER TABLE eventos_solicitacao_material
    ALTER COLUMN tipo_obra_snapshot DROP NOT NULL;

ALTER TABLE eventos_solicitacao_material
    DROP CONSTRAINT ck_eventos_solicitacao_tipo_obra;

ALTER TABLE eventos_solicitacao_material
    ADD CONSTRAINT ck_eventos_solicitacao_tipo_obra
        CHECK (
            tipo_obra_snapshot IS NULL
            OR tipo_obra_snapshot IN ('OBRA', 'CORRETIVA', 'PREVENTIVA', 'LPT')
        );

-- Todos os registros anteriores ao fluxo tecnico sao comerciais.
UPDATE solicitacoes_material
SET contexto = 'COMERCIAL'
WHERE contexto IS NULL;

ALTER TABLE solicitacoes_material
    ALTER COLUMN contexto SET DEFAULT 'COMERCIAL',
    ALTER COLUMN contexto SET NOT NULL;

-- Somente estados que demonstram um fato fisico permitem inferencia segura.
UPDATE solicitacoes_material
SET status_separacao = CASE
    WHEN status = 'APROVADO' THEN 'PRONTO_PARA_SEPARACAO'
    WHEN status IN ('ENTREGUE_PARCIAL', 'ENTREGUE', 'ESTORNADO') THEN 'SEPARADO'
    ELSE NULL
END
WHERE contexto = 'COMERCIAL';

-- O legado guarda quantidade liquida, exceto no estorno total, no qual a
-- quantidade entregue e preservada pela API antiga. A incerteza de estornos
-- parciais anteriores fica documentada no LEGACY_BASELINE.
UPDATE itens_solicitacao item
SET quantidade_entregue_bruta = COALESCE(item.quantidade_entregue, 0),
    quantidade_entrega_estornada = CASE
        WHEN solicitacao.status = 'ESTORNADO' THEN COALESCE(item.quantidade_entregue, 0)
        ELSE 0
    END,
    quantidade_devolvida_bruta = 0,
    quantidade_devolucao_estornada = 0
FROM solicitacoes_material solicitacao
WHERE solicitacao.id = item.id_solicitacao
  AND solicitacao.contexto = 'COMERCIAL';

ALTER TABLE itens_solicitacao
    ADD CONSTRAINT ck_itens_solicitacao_estorno_ate_entrega_bruta
        CHECK (quantidade_entrega_estornada <= quantidade_entregue_bruta),
    ADD CONSTRAINT ck_itens_solicitacao_estorno_ate_devolucao_bruta
        CHECK (quantidade_devolucao_estornada <= quantidade_devolvida_bruta);

-- Um baseline por cadeia comercial. Hashes e versao de chave nulos fazem a
-- consulta classificar a cadeia como NAO_VERIFICADO sem inventar fatos.
INSERT INTO eventos_solicitacao_material (
    id,
    escopo,
    id_agrupador,
    id_solicitacao,
    sequencia,
    tipo_evento,
    tipo_tecnico,
    tipo_obra_snapshot,
    ocorrencia,
    id_autor,
    autor_snapshot,
    origem,
    justificativa,
    versao_agregado,
    payload_canonico,
    hash_anterior,
    hash_atual,
    versao_chave,
    ocorrido_em
)
SELECT
    gen_random_uuid(),
    'SOLICITACAO',
    NULL,
    solicitacao.id,
    1,
    'LEGACY_BASELINE',
    NULL,
    NULL,
    NULL,
    NULL,
    'SISTEMA',
    'SYSTEM',
    'Estado anterior a trilha verificavel',
    COALESCE(solicitacao.versao, 0),
    jsonb_build_object(
        'integridade', 'NAO_VERIFICADO',
        'contexto', 'COMERCIAL',
        'status', solicitacao.status,
        'statusSeparacao', solicitacao.status_separacao,
        'quantidadeSolicitada', COALESCE(itens.quantidade_solicitada, 0),
        'quantidadeEntregueLegada', COALESCE(itens.quantidade_entregue, 0),
        'itens', COALESCE(itens.snapshot_itens, '[]'::jsonb)
    ),
    NULL,
    NULL,
    NULL,
    COALESCE(solicitacao.data_solicitacao, CURRENT_TIMESTAMP)
FROM solicitacoes_material solicitacao
LEFT JOIN LATERAL (
    SELECT
        sum(item.quantidade) AS quantidade_solicitada,
        sum(item.quantidade_entregue) AS quantidade_entregue,
        jsonb_agg(
            jsonb_build_object(
                'idItem', item.id,
                'idMaterial', item.id_material,
                'quantidade', item.quantidade,
                'quantidadeEntregue', item.quantidade_entregue,
                'entregueBruta', item.quantidade_entregue_bruta,
                'entregaEstornada', item.quantidade_entrega_estornada
            )
            ORDER BY item.id
        ) AS snapshot_itens
    FROM itens_solicitacao item
    WHERE item.id_solicitacao = solicitacao.id
) itens ON TRUE
WHERE solicitacao.contexto = 'COMERCIAL';
