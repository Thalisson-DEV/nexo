\set ON_ERROR_STOP on

-- Reconciliacao da fixture V114 depois da aplicacao de V115..V125.
-- Qualquer divergencia interrompe o psql com erro.

DO $$
DECLARE
    divergencias BIGINT;
BEGIN
    WITH atual(dataset, row_count, digest) AS (
        SELECT 'obras', count(*),
               md5(COALESCE(string_agg(
                   (to_jsonb(registro) - ARRAY['tipo_obra'])::text,
                   '|' ORDER BY id::text), ''))
        FROM obras registro
        UNION ALL
        SELECT 'materiais', count(*),
               md5(COALESCE(string_agg(to_jsonb(registro)::text,
                   '|' ORDER BY id::text), ''))
        FROM materiais registro
        UNION ALL
        SELECT 'solicitacoes_material', count(*),
               md5(COALESCE(string_agg(
                   (to_jsonb(registro) - ARRAY[
                       'contexto', 'tipo_tecnico', 'id_obra',
                       'tipo_obra_snapshot', 'id_ocorrencia',
                       'codigo_ocorrencia_snapshot',
                       'id_almoxarifado_atendimento', 'id_equipe_saque',
                       'data_prevista_retirada', 'observacao',
                       'status_separacao', 'separacao_iniciada_em',
                       'id_usuario_inicio_separacao', 'separacao_concluida_em',
                       'id_usuario_conclusao_separacao', 'encerrada_em',
                       'id_usuario_encerramento', 'motivo_encerramento',
                       'motivo_cancelamento'
                   ])::text, '|' ORDER BY id::text), ''))
        FROM solicitacoes_material registro
        UNION ALL
        SELECT 'itens_solicitacao', count(*),
               md5(COALESCE(string_agg(
                   (to_jsonb(registro) - ARRAY[
                       'quantidade_entregue_bruta',
                       'quantidade_entrega_estornada',
                       'quantidade_devolvida_bruta',
                       'quantidade_devolucao_estornada'
                   ])::text, '|' ORDER BY id::text), ''))
        FROM itens_solicitacao registro
        UNION ALL
        SELECT 'estoques', count(*),
               md5(COALESCE(string_agg(
                   (to_jsonb(registro) - ARRAY['id_obra'])::text,
                   '|' ORDER BY id::text), ''))
        FROM estoques registro
        WHERE nome_identificador <> 'ESTOQUE OBRA VERIFICACAO'
        UNION ALL
        SELECT 'movimentacoes_estoque', count(*),
               md5(COALESCE(string_agg(
                   (to_jsonb(registro) - ARRAY[
                       'id_solicitacao', 'tipo_tecnico',
                       'tipo_obra_snapshot', 'id_ocorrencia',
                       'codigo_ocorrencia_snapshot', 'id_reserva',
                       'id_operacao', 'id_item_operacao', 'id_obra', 'id_equipe'
                   ])::text, '|' ORDER BY id::text), ''))
        FROM movimentacoes_estoque registro
        UNION ALL
        SELECT 'saldos', count(*),
               md5(COALESCE(string_agg(to_jsonb(registro)::text,
                   '|' ORDER BY id::text), ''))
        FROM saldos registro
        UNION ALL
        SELECT 'saldos_seriais', count(*),
               md5(COALESCE(string_agg(to_jsonb(registro)::text,
                   '|' ORDER BY id::text), ''))
        FROM saldos_seriais registro
    )
    SELECT count(*) INTO divergencias
    FROM migration_verification_snapshot esperado
    FULL JOIN atual USING (dataset)
    WHERE esperado.dataset IS NULL
       OR atual.dataset IS NULL
       OR esperado.row_count <> atual.row_count
       OR esperado.digest <> atual.digest;

    IF divergencias <> 0 THEN
        RAISE EXCEPTION 'Dados legados divergiram em % dataset(s)', divergencias;
    END IF;

    IF (SELECT count(*) FROM obras WHERE tipo_obra <> 'OBRA') <> 0 THEN
        RAISE EXCEPTION 'Backfill de obras nao resultou em OBRA';
    END IF;

    IF (SELECT count(*) FROM solicitacoes_material
        WHERE contexto <> 'COMERCIAL'
           OR tipo_tecnico IS NOT NULL
           OR id_obra IS NOT NULL) <> 0 THEN
        RAISE EXCEPTION 'Solicitacao legada recebeu contexto ou vinculo tecnico';
    END IF;

    IF (SELECT count(*) FROM obras_relacionamentos) <> 2
       OR (SELECT count(*) FROM obras_relacionamentos WHERE tipo = 'KIT') <> 1
       OR (SELECT count(*) FROM obras_relacionamentos WHERE tipo = 'MEDIDOR') <> 1 THEN
        RAISE EXCEPTION 'Relacionamentos KIT/MEDIDOR nao foram preservados';
    END IF;

    IF (SELECT count(*) FROM eventos_solicitacao_material
        WHERE tipo_evento = 'LEGACY_BASELINE') <> 5
       OR (SELECT count(DISTINCT id_solicitacao)
           FROM eventos_solicitacao_material
           WHERE tipo_evento = 'LEGACY_BASELINE') <> 5
       OR EXISTS (
           SELECT 1 FROM eventos_solicitacao_material
           WHERE tipo_evento = 'LEGACY_BASELINE'
             AND (payload_canonico->>'integridade' <> 'NAO_VERIFICADO'
                  OR hash_anterior IS NOT NULL
                  OR hash_atual IS NOT NULL
                  OR versao_chave IS NOT NULL)
       ) THEN
        RAISE EXCEPTION 'Baselines legados invalidos';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM itens_solicitacao item
        JOIN solicitacoes_material solicitacao ON solicitacao.id = item.id_solicitacao
        WHERE item.quantidade_entregue_bruta <> item.quantidade_entregue
           OR item.quantidade_devolvida_bruta <> 0
           OR item.quantidade_devolucao_estornada <> 0
           OR item.quantidade_entrega_estornada <>
              CASE WHEN solicitacao.status = 'ESTORNADO'
                   THEN item.quantidade_entregue ELSE 0 END
    ) THEN
        RAISE EXCEPTION 'Acumuladores legados divergentes';
    END IF;

    IF EXISTS (
        SELECT 1 FROM solicitacoes_material
        WHERE status_separacao IS DISTINCT FROM CASE
            WHEN status = 'APROVADO' THEN 'PRONTO_PARA_SEPARACAO'
            WHEN status IN ('ENTREGUE_PARCIAL', 'ENTREGUE', 'ESTORNADO')
                THEN 'SEPARADO'
            ELSE NULL
        END
    ) THEN
        RAISE EXCEPTION 'Status de separacao legado divergente';
    END IF;

    IF EXISTS (SELECT 1 FROM estoques WHERE id_obra IS NOT NULL)
       OR (SELECT count(*) FROM tipos_estoque WHERE nome = 'OBRA') <> 1 THEN
        RAISE EXCEPTION 'Migration criou estoque patrimonial ou tipo OBRA invalido';
    END IF;

    IF EXISTS (
        SELECT 1 FROM movimentacoes_estoque
        WHERE id_solicitacao IS NOT NULL
           OR tipo_tecnico IS NOT NULL
           OR id_operacao IS NOT NULL
           OR id_item_operacao IS NOT NULL
           OR id_obra IS NOT NULL
           OR id_equipe IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'Movimento legado recebeu correlacao tecnica';
    END IF;

    IF (SELECT count(*) FROM reservas_solicitacao_tecnica) <> 0
       OR (SELECT count(*) FROM operacoes_solicitacao_material) <> 0
       OR (SELECT count(*) FROM itens_operacao_solicitacao) <> 0
       OR (SELECT count(*) FROM comandos_idempotentes_solicitacao) <> 0
       OR (SELECT count(*) FROM lotes_importacao_solicitacao) <> 0 THEN
        RAISE EXCEPTION 'Backfill inventou fatos tecnicos';
    END IF;
END;
$$;

DO $$
DECLARE
    definicao_indice TEXT;
BEGIN
    IF (SELECT count(*)
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'itens_solicitacao'
          AND column_name IN (
              'quantidade_entregue_bruta', 'quantidade_entrega_estornada',
              'quantidade_devolvida_bruta', 'quantidade_devolucao_estornada')
          AND numeric_precision = 12
          AND numeric_scale = 3) <> 4 THEN
        RAISE EXCEPTION 'Precisao dos acumuladores tecnicos diverge de NUMERIC(12,3)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'reservas_solicitacao_tecnica'
          AND column_name = 'numero'
          AND numeric_precision = 20
          AND numeric_scale = 0
    ) THEN
        RAISE EXCEPTION 'Precisao do numero de reserva diverge de NUMERIC(20,0)';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'reservas_solicitacao_tecnica'
          AND constraint_name = 'uq_reservas_solicitacao_filho'
          AND constraint_type = 'UNIQUE'
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'uq_estoques_id_obra'
    ) THEN
        RAISE EXCEPTION 'Unicidades de reserva/estoque da obra ausentes';
    END IF;

    SELECT indexdef INTO definicao_indice
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND indexname = 'idx_solicitacoes_material_obra_ocorrencia';
    IF definicao_indice IS NULL
       OR definicao_indice NOT ILIKE '%WHERE%contexto%TECNICO%id_ocorrencia IS NOT NULL%' THEN
        RAISE EXCEPTION 'Indice parcial de solicitacao por obra/ocorrencia ausente';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.triggers
        WHERE event_object_schema = 'public'
          AND event_object_table = 'eventos_solicitacao_material'
          AND trigger_name = 'trg_eventos_solicitacao_append_only'
    ) THEN
        RAISE EXCEPTION 'Trigger append-only ausente';
    END IF;
END;
$$;

-- O trigger append-only precisa rejeitar tanto UPDATE quanto DELETE com 55000.
DO $$
DECLARE
    evento_id UUID;
BEGIN
    SELECT id INTO evento_id
    FROM eventos_solicitacao_material
    WHERE tipo_evento = 'LEGACY_BASELINE'
    ORDER BY id
    LIMIT 1;

    BEGIN
        UPDATE eventos_solicitacao_material
        SET justificativa = 'mutacao proibida'
        WHERE id = evento_id;
        RAISE EXCEPTION 'Trigger append-only aceitou UPDATE';
    EXCEPTION
        WHEN SQLSTATE '55000' THEN NULL;
    END;

    BEGIN
        DELETE FROM eventos_solicitacao_material WHERE id = evento_id;
        RAISE EXCEPTION 'Trigger append-only aceitou DELETE';
    EXCEPTION
        WHEN SQLSTATE '55000' THEN NULL;
    END;

    -- A role da aplicacao nao pode liberar mutacoes por configuracao de sessao.
    PERFORM set_config('ces.audit.allow_event_mutation', 'true', TRUE);
    BEGIN
        UPDATE eventos_solicitacao_material
        SET justificativa = 'bypass proibido'
        WHERE id = evento_id;
        RAISE EXCEPTION 'Trigger append-only aceitou bypass por GUC';
    EXCEPTION
        WHEN SQLSTATE '55000' THEN NULL;
    END;
    PERFORM set_config('ces.audit.allow_event_mutation', 'false', TRUE);
END;
$$;

SELECT 'RECONCILIACAO_V125_OK' AS resultado,
       (SELECT count(*) FROM obras) AS obras,
       (SELECT count(*) FROM materiais) AS materiais,
       (SELECT count(*) FROM solicitacoes_material) AS solicitacoes,
       (SELECT count(*) FROM eventos_solicitacao_material
        WHERE tipo_evento = 'LEGACY_BASELINE') AS baselines;
