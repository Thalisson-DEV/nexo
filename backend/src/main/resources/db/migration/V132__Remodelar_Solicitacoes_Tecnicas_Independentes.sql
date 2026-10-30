-- Remodela o schema tecnico criado em V117-V123 para o contrato final.
-- A migracao e aditiva no backfill: dados sem associacao deterministica sao
-- preservados em tabelas de legado antes da remocao das colunas obsoletas.

CREATE TABLE IF NOT EXISTS ocorrencias_obras (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_obra UUID NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
    codigo_normalizado VARCHAR(100) NOT NULL,
    versao BIGINT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_ocorrencias_obras_obra_codigo UNIQUE (id_obra, codigo_normalizado)
);

CREATE INDEX IF NOT EXISTS idx_ocorrencias_obras_obra
    ON ocorrencias_obras(id_obra);

-- Arquivos de legado mantem fatos que nao podem ser atribuidos a uma unica
-- solicitacao sem inventar uma relacao.
CREATE TABLE IF NOT EXISTS legado_agrupamento_solicitacao_tecnica (
    id UUID PRIMARY KEY,
    dados JSONB NOT NULL,
    arquivado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legado_evento_solicitacao_material (
    id_evento UUID PRIMARY KEY,
    dados JSONB NOT NULL,
    arquivado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legado_comando_solicitacao_material (
    id_comando UUID PRIMARY KEY,
    dados JSONB NOT NULL,
    arquivado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legado_classificacao_material (
    id_material UUID PRIMARY KEY,
    classificacao VARCHAR(20),
    arquivado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legado_classificacao_item_solicitacao (
    id_item UUID PRIMARY KEY,
    direcao VARCHAR(15),
    classificacao VARCHAR(20),
    arquivado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legado_lote_importacao_xlsx (
    id_lote UUID PRIMARY KEY,
    dados JSONB NOT NULL,
    arquivado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS legado_lote_importacao_solicitacao (
    id_lote UUID PRIMARY KEY,
    dados JSONB NOT NULL,
    arquivado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO legado_agrupamento_solicitacao_tecnica (id, dados)
SELECT agrupamento.id, to_jsonb(agrupamento)
FROM agrupamentos_solicitacao_tecnica agrupamento
ON CONFLICT (id) DO NOTHING;

INSERT INTO legado_classificacao_material (id_material, classificacao)
SELECT id, classificacao_tecnica
FROM materiais
WHERE classificacao_tecnica IS NOT NULL
ON CONFLICT (id_material) DO NOTHING;

INSERT INTO legado_classificacao_item_solicitacao (id_item, direcao, classificacao)
SELECT id, direcao, classificacao_tecnica_snapshot
FROM itens_solicitacao
WHERE direcao IS NOT NULL OR classificacao_tecnica_snapshot IS NOT NULL
ON CONFLICT (id_item) DO NOTHING;

-- XLSX nao possui representacao no contrato final; lotes antigos ficam
-- recuperaveis sem bloquear a conversao da tabela ativa para CSV.
INSERT INTO legado_lote_importacao_solicitacao (id_lote, dados)
SELECT id, to_jsonb(lote)
FROM lotes_importacao_solicitacao lote
WHERE formato <> 'CSV'
ON CONFLICT (id_lote) DO NOTHING;
DELETE FROM lotes_importacao_solicitacao
WHERE formato <> 'CSV';

-- Uma ocorrencia pode ter sido persistida no agrupador ou copiada em uma
-- solicitacao/operacao. Todas as fontes convergem para o mesmo codigo da obra.
WITH codigos AS (
    SELECT id_obra, ocorrencia AS codigo
    FROM agrupamentos_solicitacao_tecnica
    WHERE id_obra IS NOT NULL AND btrim(ocorrencia) <> ''
      AND tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
    UNION
    SELECT id_obra, ocorrencia
    FROM solicitacoes_material
    WHERE id_obra IS NOT NULL AND btrim(ocorrencia) <> ''
      AND tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
    UNION
    SELECT id_obra, ocorrencia
    FROM operacoes_solicitacao_material
    WHERE id_obra IS NOT NULL AND btrim(ocorrencia) <> ''
      AND tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
)
INSERT INTO ocorrencias_obras (id, id_obra, codigo_normalizado, usuario_criacao)
SELECT gen_random_uuid(), id_obra,
       upper(regexp_replace(normalize(btrim(codigo), NFKC), '[[:space:]]+', ' ', 'g')),
       'migration:V132'
FROM codigos
WHERE length(upper(regexp_replace(normalize(btrim(codigo), NFKC), '[[:space:]]+', ' ', 'g'))) <= 100
ON CONFLICT (id_obra, codigo_normalizado) DO NOTHING;

ALTER TABLE solicitacoes_material
    ADD COLUMN IF NOT EXISTS id_ocorrencia UUID,
    ADD COLUMN IF NOT EXISTS codigo_ocorrencia_snapshot VARCHAR(100);

ALTER TABLE operacoes_solicitacao_material
    ADD COLUMN IF NOT EXISTS id_ocorrencia UUID,
    ADD COLUMN IF NOT EXISTS codigo_ocorrencia_snapshot VARCHAR(100);

ALTER TABLE movimentacoes_estoque
    ADD COLUMN IF NOT EXISTS id_ocorrencia UUID,
    ADD COLUMN IF NOT EXISTS codigo_ocorrencia_snapshot VARCHAR(100);

ALTER TABLE eventos_solicitacao_material
    ADD COLUMN IF NOT EXISTS id_ocorrencia UUID,
    ADD COLUMN IF NOT EXISTS codigo_ocorrencia_snapshot VARCHAR(100);

ALTER TABLE eventos_solicitacao_material
    RENAME COLUMN versao_agregado TO versao_solicitacao;

-- O snapshot da solicitacao e a fonte de verdade dos seus fatos derivados.
UPDATE solicitacoes_material solicitacao
SET id_ocorrencia = ocorrencia.id,
    codigo_ocorrencia_snapshot = ocorrencia.codigo_normalizado
FROM ocorrencias_obras ocorrencia
WHERE solicitacao.id_obra = ocorrencia.id_obra
  AND solicitacao.ocorrencia IS NOT NULL
  AND ocorrencia.codigo_normalizado = upper(regexp_replace(
      normalize(btrim(solicitacao.ocorrencia), NFKC), '[[:space:]]+', ' ', 'g'));

UPDATE operacoes_solicitacao_material operacao
SET id_ocorrencia = solicitacao.id_ocorrencia,
    codigo_ocorrencia_snapshot = solicitacao.codigo_ocorrencia_snapshot,
    id_obra = COALESCE(operacao.id_obra, solicitacao.id_obra),
    tipo_tecnico = COALESCE(operacao.tipo_tecnico, solicitacao.tipo_tecnico),
    tipo_obra_snapshot = COALESCE(operacao.tipo_obra_snapshot, solicitacao.tipo_obra_snapshot)
FROM solicitacoes_material solicitacao
WHERE operacao.id_solicitacao = solicitacao.id;

UPDATE movimentacoes_estoque movimento
SET id_operacao = COALESCE(movimento.id_operacao, operacao.id),
    id_solicitacao = COALESCE(movimento.id_solicitacao, operacao.id_solicitacao),
    id_obra = COALESCE(movimento.id_obra, operacao.id_obra),
    tipo_tecnico = COALESCE(movimento.tipo_tecnico, operacao.tipo_tecnico),
    tipo_obra_snapshot = COALESCE(movimento.tipo_obra_snapshot, operacao.tipo_obra_snapshot),
    id_ocorrencia = operacao.id_ocorrencia,
    codigo_ocorrencia_snapshot = operacao.codigo_ocorrencia_snapshot
FROM operacoes_solicitacao_material operacao
WHERE movimento.id_operacao = operacao.id
   OR movimento.id_referencia_operacao = operacao.id;

UPDATE movimentacoes_estoque movimento
SET id_solicitacao = COALESCE(movimento.id_solicitacao, solicitacao.id),
    id_obra = COALESCE(movimento.id_obra, solicitacao.id_obra),
    tipo_tecnico = COALESCE(movimento.tipo_tecnico, solicitacao.tipo_tecnico),
    tipo_obra_snapshot = COALESCE(movimento.tipo_obra_snapshot, solicitacao.tipo_obra_snapshot),
    id_ocorrencia = solicitacao.id_ocorrencia,
    codigo_ocorrencia_snapshot = solicitacao.codigo_ocorrencia_snapshot
FROM solicitacoes_material solicitacao
WHERE movimento.id_solicitacao = solicitacao.id;

UPDATE eventos_solicitacao_material evento
SET id_ocorrencia = solicitacao.id_ocorrencia,
    codigo_ocorrencia_snapshot = solicitacao.codigo_ocorrencia_snapshot,
    tipo_tecnico = COALESCE(evento.tipo_tecnico, solicitacao.tipo_tecnico),
    tipo_obra_snapshot = COALESCE(evento.tipo_obra_snapshot, solicitacao.tipo_obra_snapshot)
FROM solicitacoes_material solicitacao
WHERE evento.id_solicitacao = solicitacao.id;

DO $$
DECLARE
    inconsistencias BIGINT;
BEGIN
    SELECT COUNT(*) INTO inconsistencias
    FROM solicitacoes_material
    WHERE contexto = 'TECNICO'
      AND tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
      AND (id_ocorrencia IS NULL OR codigo_ocorrencia_snapshot IS NULL);
    IF inconsistencias > 0 THEN
        RAISE EXCEPTION
            'V132 interrompida: % solicitacao(oes) tecnica(s) nao possuem ocorrencia deterministica.',
            inconsistencias
            USING ERRCODE = '23514';
    END IF;
END
$$;

-- Eventos e comandos de escopo agrupado nao possuem uma solicitacao unica.
-- O payload original fica preservado e somente a cadeia final e mantida.
INSERT INTO legado_evento_solicitacao_material (id_evento, dados)
SELECT id, to_jsonb(evento)
FROM eventos_solicitacao_material evento
WHERE evento.escopo <> 'SOLICITACAO' OR evento.id_solicitacao IS NULL
ON CONFLICT (id_evento) DO NOTHING;

INSERT INTO legado_comando_solicitacao_material (id_comando, dados)
SELECT id, to_jsonb(comando)
FROM comandos_idempotentes_solicitacao comando
WHERE comando.id_agrupador IS NOT NULL
ON CONFLICT (id_comando) DO NOTHING;

DROP TRIGGER IF EXISTS trg_eventos_solicitacao_append_only ON eventos_solicitacao_material;
DELETE FROM eventos_solicitacao_material
WHERE escopo <> 'SOLICITACAO' OR id_solicitacao IS NULL;
DELETE FROM comandos_idempotentes_solicitacao
WHERE id_agrupador IS NOT NULL;

-- Restringe ocorrencia, cadeia de eventos e movimento ao modelo final.
DROP TRIGGER IF EXISTS trg_solicitacoes_material_coerencia_agrupamento
    ON solicitacoes_material;
DROP FUNCTION IF EXISTS validar_coerencia_filho_solicitacao_tecnica();

ALTER TABLE solicitacoes_material
    DROP CONSTRAINT IF EXISTS ck_solicitacoes_material_filho_tecnico_completo,
    DROP CONSTRAINT IF EXISTS ck_solicitacoes_material_ocorrencia_por_tipo_obra;
ALTER TABLE operacoes_solicitacao_material
    DROP CONSTRAINT IF EXISTS ck_operacoes_solicitacao_ocorrencia_por_tipo_obra;
ALTER TABLE movimentacoes_estoque
    DROP CONSTRAINT IF EXISTS ck_movimentacoes_estoque_contexto_tecnico,
    DROP CONSTRAINT IF EXISTS ck_movimentacoes_estoque_ocorrencia_por_tipo_obra;
ALTER TABLE eventos_solicitacao_material
    DROP CONSTRAINT IF EXISTS ck_eventos_solicitacao_cadeia,
    DROP CONSTRAINT IF EXISTS ck_eventos_solicitacao_escopo;

ALTER TABLE solicitacoes_material
    ADD CONSTRAINT ck_solicitacoes_material_tecnica_final CHECK (
        contexto <> 'TECNICO'
        OR (tipo_tecnico IN ('221', '921', '222', '922') AND id_obra IS NOT NULL
            AND tipo_obra_snapshot IS NOT NULL)
    ),
    ADD CONSTRAINT ck_solicitacoes_material_ocorrencia_final CHECK (
        contexto <> 'TECNICO'
        OR (tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
            AND id_ocorrencia IS NOT NULL AND codigo_ocorrencia_snapshot IS NOT NULL
            AND btrim(codigo_ocorrencia_snapshot) <> '')
        OR (tipo_obra_snapshot IN ('OBRA', 'LPT')
            AND id_ocorrencia IS NULL AND codigo_ocorrencia_snapshot IS NULL)
    );

ALTER TABLE operacoes_solicitacao_material
    ADD CONSTRAINT ck_operacoes_solicitacao_ocorrencia_final CHECK (
        (tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
            AND id_ocorrencia IS NOT NULL AND codigo_ocorrencia_snapshot IS NOT NULL)
        OR (tipo_obra_snapshot IN ('OBRA', 'LPT')
            AND id_ocorrencia IS NULL AND codigo_ocorrencia_snapshot IS NULL)
    );

ALTER TABLE movimentacoes_estoque
    ADD CONSTRAINT ck_movimentacoes_estoque_contexto_tecnico_final CHECK (
        tipo_tecnico IS NULL
        OR (id_solicitacao IS NOT NULL AND id_operacao IS NOT NULL
            AND id_item_operacao IS NOT NULL AND id_obra IS NOT NULL
            AND tipo_obra_snapshot IS NOT NULL)
    ),
    ADD CONSTRAINT ck_movimentacoes_estoque_ocorrencia_final CHECK (
        tipo_tecnico IS NULL
        OR (tipo_obra_snapshot IN ('CORRETIVA', 'PREVENTIVA')
            AND id_ocorrencia IS NOT NULL AND codigo_ocorrencia_snapshot IS NOT NULL)
        OR (tipo_obra_snapshot IN ('OBRA', 'LPT')
            AND id_ocorrencia IS NULL AND codigo_ocorrencia_snapshot IS NULL)
    );

ALTER TABLE eventos_solicitacao_material
    ADD CONSTRAINT ck_eventos_solicitacao_escopo_final CHECK (
        escopo = 'SOLICITACAO'
    ),
    ADD CONSTRAINT ck_eventos_solicitacao_cadeia_final CHECK (
        escopo = 'SOLICITACAO' AND id_solicitacao IS NOT NULL
    );

-- Chaves estrangeiras aditivas para ocorrencia e obra.
DO $$
DECLARE
    tabela TEXT;
BEGIN
    FOREACH tabela IN ARRAY ARRAY[
        'solicitacoes_material', 'operacoes_solicitacao_material',
        'movimentacoes_estoque', 'eventos_solicitacao_material']
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = 'fk_' || tabela || '_ocorrencia') THEN
            EXECUTE format(
                'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (id_ocorrencia) REFERENCES ocorrencias_obras(id) ON DELETE RESTRICT',
                tabela, 'fk_' || tabela || '_ocorrencia');
        END IF;
    END LOOP;
END
$$;

-- Reserva de tecnicos pode existir sem agente.
ALTER TABLE reservas_solicitacao_tecnica
    ALTER COLUMN id_agente_responsavel DROP NOT NULL,
    ALTER COLUMN nome_agente_snapshot DROP NOT NULL;

ALTER TABLE lotes_importacao_solicitacao
    ADD COLUMN IF NOT EXISTS total_solicitacoes INTEGER;

UPDATE lotes_importacao_solicitacao
SET total_solicitacoes = CASE
    WHEN jsonb_typeof(previa -> 'solicitacoesPrevistas') = 'array'
        THEN jsonb_array_length(previa -> 'solicitacoesPrevistas')
    ELSE COALESCE(total_agrupamentos, 0)
END
WHERE total_solicitacoes IS NULL;

ALTER TABLE lotes_importacao_solicitacao
    ALTER COLUMN total_solicitacoes SET DEFAULT 0,
    ALTER COLUMN total_solicitacoes SET NOT NULL;

ALTER TABLE lotes_importacao_solicitacao
    DROP CONSTRAINT IF EXISTS ck_lotes_importacao_formato;

INSERT INTO legado_lote_importacao_xlsx (id_lote, dados)
SELECT id, to_jsonb(lote)
FROM lotes_importacao_solicitacao lote
WHERE formato <> 'CSV'
ON CONFLICT (id_lote) DO NOTHING;

DELETE FROM lotes_importacao_solicitacao
WHERE formato <> 'CSV';

ALTER TABLE lotes_importacao_solicitacao
    ADD CONSTRAINT ck_lotes_importacao_formato_csv CHECK (formato = 'CSV');

-- Remove checks, indices e colunas exclusivamente tecnicos legados depois do
-- backfill. A classificacao material tambem e arquivada antes da remocao.
ALTER TABLE materiais DROP CONSTRAINT IF EXISTS ck_materiais_classificacao_tecnica;
DROP INDEX IF EXISTS idx_materiais_classificacao_tecnica;
ALTER TABLE materiais DROP COLUMN IF EXISTS classificacao_tecnica;

ALTER TABLE itens_solicitacao
    DROP CONSTRAINT IF EXISTS ck_itens_solicitacao_direcao,
    DROP CONSTRAINT IF EXISTS ck_itens_solicitacao_classificacao_tecnica;
ALTER TABLE itens_solicitacao
    DROP COLUMN IF EXISTS direcao,
    DROP COLUMN IF EXISTS classificacao_tecnica_snapshot;

ALTER TABLE itens_operacao_solicitacao
    DROP CONSTRAINT IF EXISTS ck_itens_operacao_classificacao;
ALTER TABLE itens_operacao_solicitacao
    DROP COLUMN IF EXISTS classificacao_tecnica_snapshot;

ALTER TABLE lotes_importacao_solicitacao
    DROP COLUMN IF EXISTS total_agrupamentos;

DROP INDEX IF EXISTS uq_solicitacoes_material_agrupamento_tipo;
DROP INDEX IF EXISTS idx_solicitacoes_material_agrupamento;
DROP INDEX IF EXISTS idx_operacoes_solicitacao_agrupamento;
DROP INDEX IF EXISTS idx_movimentacoes_estoque_agrupador;
DROP INDEX IF EXISTS uq_eventos_solicitacao_cadeia_agrupador;
DROP INDEX IF EXISTS uq_eventos_solicitacao_cadeia_filho;
DROP INDEX IF EXISTS idx_comandos_idempotentes_solicitacao;

-- V119 criou algumas FKs sem nome explicito. Remove-as por referencia para
-- permitir o DROP COLUMN, sem depender do nome gerado pelo PostgreSQL.
DO $$
DECLARE
    referencia RECORD;
BEGIN
    IF to_regclass('public.agrupamentos_solicitacao_tecnica') IS NOT NULL THEN
        FOR referencia IN
            SELECT conrelid::regclass AS tabela, conname
            FROM pg_constraint
            WHERE contype = 'f'
              AND confrelid = 'public.agrupamentos_solicitacao_tecnica'::regclass
        LOOP
            EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',
                           referencia.tabela, referencia.conname);
        END LOOP;
    END IF;
END
$$;

ALTER TABLE solicitacoes_material DROP CONSTRAINT IF EXISTS fk_solicitacoes_material_agrupamento;
ALTER TABLE solicitacoes_material DROP COLUMN IF EXISTS id_agrupador;
ALTER TABLE operacoes_solicitacao_material DROP COLUMN IF EXISTS id_agrupador;
ALTER TABLE movimentacoes_estoque DROP COLUMN IF EXISTS id_agrupador;
ALTER TABLE comandos_idempotentes_solicitacao DROP COLUMN IF EXISTS id_agrupador;
ALTER TABLE eventos_solicitacao_material DROP COLUMN IF EXISTS id_agrupador;

ALTER TABLE solicitacoes_material DROP COLUMN IF EXISTS ocorrencia;
ALTER TABLE solicitacoes_material DROP COLUMN IF EXISTS motivo_cancelamento;
ALTER TABLE operacoes_solicitacao_material DROP COLUMN IF EXISTS ocorrencia;
ALTER TABLE movimentacoes_estoque DROP COLUMN IF EXISTS ocorrencia;
ALTER TABLE eventos_solicitacao_material DROP COLUMN IF EXISTS ocorrencia;

ALTER TABLE solicitacoes_material
    ADD CONSTRAINT fk_solicitacoes_material_obra_final
        FOREIGN KEY (id_obra) REFERENCES obras(id) ON DELETE RESTRICT;

ALTER TABLE eventos_solicitacao_material
    ALTER COLUMN id_solicitacao SET NOT NULL;

DROP INDEX IF EXISTS idx_eventos_solicitacao_ocorrido_em;
CREATE UNIQUE INDEX IF NOT EXISTS uq_eventos_solicitacao_cadeia_final
    ON eventos_solicitacao_material(id_solicitacao, sequencia);
CREATE INDEX IF NOT EXISTS idx_eventos_solicitacao_ocorrido_em_final
    ON eventos_solicitacao_material(ocorrido_em DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_solicitacoes_material_obra_ocorrencia_final
    ON solicitacoes_material(id_obra, id_ocorrencia)
    WHERE contexto = 'TECNICO' AND id_ocorrencia IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_movimentacoes_estoque_obra_ocorrencia_final
    ON movimentacoes_estoque(id_obra, id_ocorrencia)
    WHERE tipo_tecnico IS NOT NULL AND id_ocorrencia IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_comandos_idempotentes_solicitacao_final
    ON comandos_idempotentes_solicitacao(id_solicitacao);

CREATE TRIGGER trg_eventos_solicitacao_append_only
BEFORE UPDATE OR DELETE ON eventos_solicitacao_material
FOR EACH ROW EXECUTE FUNCTION bloquear_mutacao_eventos_solicitacao();

DROP TABLE IF EXISTS agrupamentos_solicitacao_tecnica;
