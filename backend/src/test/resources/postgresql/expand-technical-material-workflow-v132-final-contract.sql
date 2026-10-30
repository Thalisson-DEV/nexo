\set ON_ERROR_STOP on

-- Contrato estrutural do modelo final apos V132.
DO $$
DECLARE
    colunas_legadas INTEGER;
    tabelas_legadas INTEGER;
BEGIN
    IF to_regclass('public.ocorrencias_obras') IS NULL THEN
        RAISE EXCEPTION 'Tabela ocorrencias_obras ausente';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'ocorrencias_obras'
          AND column_name = 'id_obra' AND is_nullable = 'NO'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'ocorrencias_obras'
          AND column_name = 'codigo_normalizado' AND character_maximum_length = 100
    ) OR NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_ocorrencias_obras_obra_codigo'
    ) THEN
        RAISE EXCEPTION 'Contrato de ocorrencia por obra/codigo ausente';
    END IF;

    SELECT count(*) INTO colunas_legadas
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name IN ('materiais', 'solicitacoes_material', 'itens_solicitacao',
                         'operacoes_solicitacao_material', 'movimentacoes_estoque',
                         'itens_operacao_solicitacao', 'eventos_solicitacao_material',
                         'lotes_importacao_solicitacao')
      AND column_name IN ('id_agrupador', 'direcao', 'classificacao_tecnica_snapshot',
                          'classificacao_tecnica', 'ocorrencia', 'total_agrupamentos',
                          'versao_agregado');
    IF colunas_legadas <> 0 THEN
        RAISE EXCEPTION 'Colunas legadas de identidade ou classificacao ainda existem';
    END IF;

    SELECT count(*) INTO tabelas_legadas
    FROM pg_class
    WHERE relnamespace = 'public'::regnamespace
      AND relname = 'agrupamentos_solicitacao_tecnica';
    IF tabelas_legadas <> 0 THEN
        RAISE EXCEPTION 'Tabela de agrupamento ainda existe';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'solicitacoes_material'
          AND column_name IN ('id_ocorrencia', 'codigo_ocorrencia_snapshot')
        GROUP BY table_name HAVING count(*) = 2
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'reservas_solicitacao_tecnica'
          AND column_name = 'id_agente_responsavel' AND is_nullable = 'YES'
    ) THEN
        RAISE EXCEPTION 'Referencias de solicitacao ou agente opcional ausentes';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'eventos_solicitacao_material'
          AND column_name = 'id_solicitacao' AND is_nullable = 'NO'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'eventos_solicitacao_material'
          AND column_name = 'versao_solicitacao' AND is_nullable = 'NO'
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'lotes_importacao_solicitacao'
          AND column_name = 'total_solicitacoes'
    ) THEN
        RAISE EXCEPTION 'Historico por solicitacao ou total de solicitacoes ausente';
    END IF;
END;
$$;

SELECT 'CONTRATO_V132_FINAL_OK' AS resultado;
