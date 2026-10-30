-- Uma equipe representa uma unica custodia movel. Antes de criar a garantia,
-- interrompe a migracao se os dados publicados ja violarem essa invariante.
DO $$
DECLARE
    equipes_duplicadas TEXT;
BEGIN
    SELECT string_agg(id_equipe::TEXT, ', ' ORDER BY id_equipe::TEXT)
      INTO equipes_duplicadas
      FROM (
          SELECT id_equipe
            FROM estoques
           WHERE id_equipe IS NOT NULL
           GROUP BY id_equipe
          HAVING COUNT(*) > 1
      ) duplicadas;

    IF equipes_duplicadas IS NOT NULL THEN
        RAISE EXCEPTION
            'Nao foi possivel garantir um estoque por equipe. Equipes com estoques duplicados: %',
            equipes_duplicadas;
    END IF;
END $$;

-- Regulariza somente equipes ainda sem qualquer estoque vinculado. Estoques
-- existentes sao preservados, independentemente de nome, tipo ou status.
INSERT INTO estoques (
    id, nome_identificador, id_tipo_estoque, id_equipe, id_base_operacional,
    versao, data_criacao, usuario_criacao, ativo
)
SELECT
    gen_random_uuid(), UPPER(TRIM(e.cod_equipe)), tipo_campo.id, e.id,
    e.id_base_operacional, 0, CURRENT_TIMESTAMP, 'MIGRACAO_V145', TRUE
FROM equipes e
JOIN tipos_estoque tipo_campo
  ON UPPER(tipo_campo.nome) = 'CAMPO'
 AND tipo_campo.ativo = TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM estoques estoque_existente WHERE estoque_existente.id_equipe = e.id
);

-- Se CAMPO estiver ausente/inativo, o INSERT anterior nao regulariza as equipes.
-- Falhar e diagnosticar e mais seguro do que publicar equipes sem custodia.
DO $$
DECLARE
    equipes_sem_estoque TEXT;
BEGIN
    SELECT string_agg(e.id::TEXT, ', ' ORDER BY e.id::TEXT)
      INTO equipes_sem_estoque
      FROM equipes e
     WHERE NOT EXISTS (SELECT 1 FROM estoques estoque WHERE estoque.id_equipe = e.id);

    IF equipes_sem_estoque IS NOT NULL THEN
        RAISE EXCEPTION
            'Tipo de estoque CAMPO ativo ausente ou equipes nao regularizadas: %',
            equipes_sem_estoque;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_estoques_id_equipe
    ON estoques (id_equipe)
    WHERE id_equipe IS NOT NULL;
