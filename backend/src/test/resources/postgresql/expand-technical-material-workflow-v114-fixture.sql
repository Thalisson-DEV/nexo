\set ON_ERROR_STOP on

-- Fixture legada deterministica para validar V115..V125 em PostgreSQL real.
-- Deve ser aplicada somente sobre um banco isolado migrado ate V114.

SELECT id AS processo_obras_id
FROM processos_negocio WHERE nome = 'OBRAS' LIMIT 1 \gset
SELECT id AS base_operacional_id
FROM bases_operacionais ORDER BY nome, id LIMIT 1 \gset
SELECT id AS status_obra_id
FROM status_obras WHERE nome = 'PLANEJADA' LIMIT 1 \gset
SELECT id AS tipo_estoque_fisico_id
FROM tipos_estoque WHERE nome = 'FISICO' LIMIT 1 \gset

INSERT INTO colaboradores (
    id, primeiro_nome, ultimo_nome, nome_completo, nome_completo_hash,
    cpf, cpf_hash, ativo, usuario_criacao
) VALUES
    ('10000000-0000-0000-0000-000000000001', 'Coordenador', 'Legado',
     'Coordenador Legado', 'hash-coordenador-legado', '00000000001',
     'hash-cpf-coordenador-legado', TRUE, 'VERIFICACAO_MIGRACAO'),
    ('10000000-0000-0000-0000-000000000002', 'Supervisor', 'Legado',
     'Supervisor Legado', 'hash-supervisor-legado', '00000000002',
     'hash-cpf-supervisor-legado', TRUE, 'VERIFICACAO_MIGRACAO');

INSERT INTO coordenadores (
    id, id_colaborador, data_inicio, ativo, usuario_criacao
) VALUES (
    '20000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    DATE '2024-01-01', TRUE, 'VERIFICACAO_MIGRACAO'
);

INSERT INTO supervisores (
    id, id_colaborador, id_coordenador, data_inicio, ativo, usuario_criacao
) VALUES (
    '20000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000001',
    DATE '2024-01-01', TRUE, 'VERIFICACAO_MIGRACAO'
);

INSERT INTO municipios (
    id, nome, id_base_operacional, ativo, usuario_criacao
) VALUES (
    '30000000-0000-0000-0000-000000000001',
    'MUNICIPIO VERIFICACAO',
    :'base_operacional_id'::uuid,
    TRUE, 'VERIFICACAO_MIGRACAO'
);

INSERT INTO obras (
    id, numero, titulo, descricao, data_inicio, data_previsao_fim,
    id_processo, id_base_operacional, id_municipio, id_centro_custo,
    id_status_obra, id_obra_pai, tipo_vinculo_pai,
    id_supervisor, id_coordenador, ativo, usuario_criacao
) VALUES
    ('40000000-0000-0000-0000-000000000001', 'LEG0001',
     'Projeto legado principal', 'Preserva cadastro anterior',
     DATE '2024-01-01', DATE '2027-12-31',
     :'processo_obras_id'::uuid,
     :'base_operacional_id'::uuid,
     '30000000-0000-0000-0000-000000000001',
     '30000000-0000-0000-0000-000000000002',
     :'status_obra_id'::uuid, NULL, NULL,
     '20000000-0000-0000-0000-000000000002',
     '20000000-0000-0000-0000-000000000001', TRUE, 'VERIFICACAO_MIGRACAO'),
    ('40000000-0000-0000-0000-000000000002', 'LEG0002',
     'Projeto legado KIT', 'Preserva relacionamento KIT',
     DATE '2024-02-01', DATE '2027-12-31',
     :'processo_obras_id'::uuid,
     :'base_operacional_id'::uuid,
     '30000000-0000-0000-0000-000000000001',
     '30000000-0000-0000-0000-000000000002',
     :'status_obra_id'::uuid,
     '40000000-0000-0000-0000-000000000001', 'KIT',
     '20000000-0000-0000-0000-000000000002',
     '20000000-0000-0000-0000-000000000001', TRUE, 'VERIFICACAO_MIGRACAO'),
    ('40000000-0000-0000-0000-000000000003', 'LEG0003',
     'Projeto legado MEDIDOR', 'Preserva relacionamento MEDIDOR',
     DATE '2024-03-01', DATE '2027-12-31',
     :'processo_obras_id'::uuid,
     :'base_operacional_id'::uuid,
     '30000000-0000-0000-0000-000000000001',
     '30000000-0000-0000-0000-000000000002',
     :'status_obra_id'::uuid,
     '40000000-0000-0000-0000-000000000001', 'MEDIDOR',
     '20000000-0000-0000-0000-000000000002',
     '20000000-0000-0000-0000-000000000001', TRUE, 'VERIFICACAO_MIGRACAO');

INSERT INTO materiais (
    id, cod_material, descricao, unidade_medida, detalha_serie,
    id_processo, ativo, usuario_criacao
) VALUES
    ('50000000-0000-0000-0000-000000000001', 'MAT-LEG-COMUM',
     'Material legado comum', 'UN', FALSE,
     :'processo_obras_id'::uuid, TRUE, 'VERIFICACAO_MIGRACAO'),
    ('50000000-0000-0000-0000-000000000002', 'MAT-LEG-SERIAL',
     'Material legado seriado', 'UN', TRUE,
     :'processo_obras_id'::uuid, TRUE, 'VERIFICACAO_MIGRACAO');

INSERT INTO estoques (
    id, nome_identificador, id_tipo_estoque, id_base_operacional,
    ativo, usuario_criacao
) VALUES
    ('60000000-0000-0000-0000-000000000001', 'ALMOX LEGADO ORIGEM',
     :'tipo_estoque_fisico_id'::uuid,
     :'base_operacional_id'::uuid, TRUE, 'VERIFICACAO_MIGRACAO'),
    ('60000000-0000-0000-0000-000000000002', 'ALMOX LEGADO DESTINO',
     :'tipo_estoque_fisico_id'::uuid,
     :'base_operacional_id'::uuid, TRUE, 'VERIFICACAO_MIGRACAO');

INSERT INTO solicitacoes_material (
    id, id_estoque_origem, id_estoque_destino, status,
    assinatura_almox, assinatura_equipe, data_solicitacao, data_entrega,
    ativo, usuario_criacao
) VALUES
    ('70000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000002', 'PENDENTE',
     NULL, NULL, TIMESTAMPTZ '2024-06-01 10:00:00-03', NULL,
     TRUE, 'VERIFICACAO_MIGRACAO'),
    ('70000000-0000-0000-0000-000000000002',
     '60000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000002', 'APROVADO',
     NULL, NULL, TIMESTAMPTZ '2024-06-02 10:00:00-03', NULL,
     TRUE, 'VERIFICACAO_MIGRACAO'),
    ('70000000-0000-0000-0000-000000000003',
     '60000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000002', 'ENTREGUE_PARCIAL',
     'assinatura-almox-legada', 'assinatura-equipe-legada',
     TIMESTAMPTZ '2024-06-03 10:00:00-03',
     TIMESTAMPTZ '2024-06-03 14:00:00-03', TRUE, 'VERIFICACAO_MIGRACAO'),
    ('70000000-0000-0000-0000-000000000004',
     '60000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000002', 'ENTREGUE',
     'assinatura-almox-legada', 'assinatura-equipe-legada',
     TIMESTAMPTZ '2024-06-04 10:00:00-03',
     TIMESTAMPTZ '2024-06-04 14:00:00-03', TRUE, 'VERIFICACAO_MIGRACAO'),
    ('70000000-0000-0000-0000-000000000005',
     '60000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000002', 'ESTORNADO',
     'assinatura-almox-legada', 'assinatura-equipe-legada',
     TIMESTAMPTZ '2024-06-05 10:00:00-03',
     TIMESTAMPTZ '2024-06-05 14:00:00-03', TRUE, 'VERIFICACAO_MIGRACAO');

INSERT INTO itens_solicitacao (
    id, id_solicitacao, id_material, quantidade, quantidade_entregue,
    ativo, usuario_criacao
) VALUES
    ('80000000-0000-0000-0000-000000000001',
     '70000000-0000-0000-0000-000000000001',
     '50000000-0000-0000-0000-000000000001', 10.000, 0.000,
     TRUE, 'VERIFICACAO_MIGRACAO'),
    ('80000000-0000-0000-0000-000000000002',
     '70000000-0000-0000-0000-000000000002',
     '50000000-0000-0000-0000-000000000001', 12.000, 0.000,
     TRUE, 'VERIFICACAO_MIGRACAO'),
    ('80000000-0000-0000-0000-000000000003',
     '70000000-0000-0000-0000-000000000003',
     '50000000-0000-0000-0000-000000000001', 10.000, 4.500,
     TRUE, 'VERIFICACAO_MIGRACAO'),
    ('80000000-0000-0000-0000-000000000004',
     '70000000-0000-0000-0000-000000000003',
     '50000000-0000-0000-0000-000000000002', 2.000, 1.000,
     TRUE, 'VERIFICACAO_MIGRACAO'),
    ('80000000-0000-0000-0000-000000000005',
     '70000000-0000-0000-0000-000000000004',
     '50000000-0000-0000-0000-000000000001', 8.000, 8.000,
     TRUE, 'VERIFICACAO_MIGRACAO'),
    ('80000000-0000-0000-0000-000000000006',
     '70000000-0000-0000-0000-000000000005',
     '50000000-0000-0000-0000-000000000001', 6.000, 6.000,
     TRUE, 'VERIFICACAO_MIGRACAO');

INSERT INTO saldos (
    id, id_estoque, id_material, quantidade, condicao,
    ativo, usuario_criacao
) VALUES
    ('90000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000001',
     '50000000-0000-0000-0000-000000000001', 100.500, 'NOVO',
     TRUE, 'VERIFICACAO_MIGRACAO'),
    ('90000000-0000-0000-0000-000000000002',
     '60000000-0000-0000-0000-000000000002',
     '50000000-0000-0000-0000-000000000001', 20.250, 'NOVO',
     TRUE, 'VERIFICACAO_MIGRACAO');

INSERT INTO saldos_seriais (
    id, id_estoque, id_material, num_serie, condicao,
    ativo, usuario_criacao
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    '60000000-0000-0000-0000-000000000001',
    '50000000-0000-0000-0000-000000000002',
    'SERIE-LEGADA-001', 'NOVO', TRUE, 'VERIFICACAO_MIGRACAO'
);

INSERT INTO movimentacoes_estoque (
    id, id_estoque_origem, id_estoque_destino, id_material,
    quantidade, tipo_movimentacao, id_referencia_operacao,
    data_hora, num_serie, condicao, ativo, usuario_criacao
) VALUES
    ('b0000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000002',
     '50000000-0000-0000-0000-000000000001', 4.500, 'TRANSFERENCIA',
     '70000000-0000-0000-0000-000000000003',
     TIMESTAMPTZ '2024-06-03 14:00:00-03', NULL, 'NOVO',
     TRUE, 'VERIFICACAO_MIGRACAO'),
    ('b0000000-0000-0000-0000-000000000002',
     '60000000-0000-0000-0000-000000000001',
     '60000000-0000-0000-0000-000000000002',
     '50000000-0000-0000-0000-000000000002', 1.000, 'TRANSFERENCIA',
     '70000000-0000-0000-0000-000000000003',
     TIMESTAMPTZ '2024-06-03 14:00:00-03', 'SERIE-LEGADA-001', 'NOVO',
     TRUE, 'VERIFICACAO_MIGRACAO');

-- A V116 usa ON CONFLICT; estes pre-flights impedem que ambiguidades sejam
-- escondidas durante a verificacao da migracao.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM obras
        WHERE id_obra_pai IS NOT NULL
          AND (id = id_obra_pai OR tipo_vinculo_pai NOT IN ('KIT', 'MEDIDOR'))
    ) THEN
        RAISE EXCEPTION 'Fixture possui relacionamento legado invalido';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM obras
        WHERE id_obra_pai IS NOT NULL
        GROUP BY LEAST(id::text, id_obra_pai::text),
                 GREATEST(id::text, id_obra_pai::text)
        HAVING count(*) > 1 OR count(DISTINCT tipo_vinculo_pai) > 1
    ) THEN
        RAISE EXCEPTION 'Fixture possui relacionamento legado ambiguo';
    END IF;
END;
$$;

CREATE TABLE migration_verification_snapshot (
    dataset VARCHAR(50) PRIMARY KEY,
    row_count BIGINT NOT NULL,
    digest CHAR(32) NOT NULL
);

INSERT INTO migration_verification_snapshot(dataset, row_count, digest)
SELECT 'obras', count(*),
       md5(COALESCE(string_agg(to_jsonb(registro)::text, '|' ORDER BY id::text), ''))
FROM obras registro
UNION ALL
SELECT 'materiais', count(*),
       md5(COALESCE(string_agg(
           (to_jsonb(registro) - ARRAY['classificacao_tecnica'])::text,
           '|' ORDER BY id::text), ''))
FROM materiais registro
UNION ALL
SELECT 'solicitacoes_material', count(*),
       md5(COALESCE(string_agg(
           (to_jsonb(registro) - ARRAY[
               'contexto', 'tipo_tecnico', 'id_obra', 'tipo_obra_snapshot',
               'id_ocorrencia', 'codigo_ocorrencia_snapshot',
               'id_almoxarifado_atendimento', 'id_equipe_saque',
               'data_prevista_retirada', 'observacao', 'status_separacao',
               'separacao_iniciada_em', 'id_usuario_inicio_separacao',
               'separacao_concluida_em', 'id_usuario_conclusao_separacao',
               'encerrada_em', 'id_usuario_encerramento', 'motivo_encerramento',
               'motivo_cancelamento'
           ])::text, '|' ORDER BY id::text), ''))
FROM solicitacoes_material registro
UNION ALL
SELECT 'itens_solicitacao', count(*),
       md5(COALESCE(string_agg(
           (to_jsonb(registro) - ARRAY[
               'quantidade_entregue_bruta', 'quantidade_entrega_estornada',
               'quantidade_devolvida_bruta', 'quantidade_devolucao_estornada'
           ])::text, '|' ORDER BY id::text), ''))
FROM itens_solicitacao registro
UNION ALL
SELECT 'estoques', count(*),
       md5(COALESCE(string_agg(
           (to_jsonb(registro) - ARRAY['id_obra'])::text,
           '|' ORDER BY id::text), ''))
FROM estoques registro
UNION ALL
SELECT 'movimentacoes_estoque', count(*),
       md5(COALESCE(string_agg(
           (to_jsonb(registro) - ARRAY[
               'id_solicitacao', 'tipo_tecnico', 'tipo_obra_snapshot',
               'id_ocorrencia', 'codigo_ocorrencia_snapshot', 'id_reserva',
               'id_operacao', 'id_item_operacao', 'id_obra', 'id_equipe'
           ])::text, '|' ORDER BY id::text), ''))
FROM movimentacoes_estoque registro
UNION ALL
SELECT 'saldos', count(*),
       md5(COALESCE(string_agg(to_jsonb(registro)::text, '|' ORDER BY id::text), ''))
FROM saldos registro
UNION ALL
SELECT 'saldos_seriais', count(*),
       md5(COALESCE(string_agg(to_jsonb(registro)::text, '|' ORDER BY id::text), ''))
FROM saldos_seriais registro;
