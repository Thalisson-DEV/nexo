ALTER TABLE solicitacoes_material
    ADD CONSTRAINT ck_solicitacoes_material_filho_tecnico_completo
        CHECK (
            contexto IS DISTINCT FROM 'TECNICO'
            OR (
                id_agrupador IS NOT NULL
                AND tipo_tecnico IN ('221', '921', '222', '922')
                AND id_obra IS NOT NULL
                AND tipo_obra_snapshot IS NOT NULL
            )
        );

-- A coerencia entre os snapshots do agrupador e de cada filho envolve outra
-- tabela e, portanto, e garantida por trigger no momento da escrita.
CREATE FUNCTION validar_coerencia_filho_solicitacao_tecnica()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.contexto = 'TECNICO' THEN
        IF NOT EXISTS (
            SELECT 1
            FROM agrupamentos_solicitacao_tecnica agrupamento
            WHERE agrupamento.id = NEW.id_agrupador
              AND agrupamento.id_obra = NEW.id_obra
              AND agrupamento.tipo_obra_snapshot = NEW.tipo_obra_snapshot
              AND agrupamento.ocorrencia IS NOT DISTINCT FROM NEW.ocorrencia
        ) THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                CONSTRAINT = 'ck_solicitacoes_material_coerencia_agrupamento',
                MESSAGE = 'Filho tecnico diverge da obra, tipo ou ocorrencia do agrupador';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_solicitacoes_material_coerencia_agrupamento
BEFORE INSERT OR UPDATE OF contexto, id_agrupador, tipo_tecnico, id_obra, tipo_obra_snapshot, ocorrencia
ON solicitacoes_material
FOR EACH ROW
EXECUTE FUNCTION validar_coerencia_filho_solicitacao_tecnica();
