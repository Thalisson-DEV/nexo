-- Inventário físico específico da Brigada, atribuído pelo responsável à base
-- JUAZEIRO. A carga nasce em RASCUNHO: null significa ainda não inspecionado,
-- nunca conforme. A origem é o SHA-256 do arquivo normalizado de referência.

ALTER TABLE ativos_inspecionaveis_sst
    ALTER COLUMN origem TYPE VARCHAR(64);

ALTER TABLE ativos_inspecionaveis_sst
    ADD CONSTRAINT fk_ativo_inspecionavel_base
    FOREIGN KEY (id_base_operacional) REFERENCES bases_operacionais(id);

DO $$
DECLARE
    quantidade_bases INTEGER;
BEGIN
    SELECT COUNT(*) INTO quantidade_bases
    FROM bases_operacionais
    WHERE UPPER(TRIM(nome)) = 'JUAZEIRO' AND ativo;

    IF quantidade_bases <> 1 THEN
        RAISE EXCEPTION
            'A carga dos ativos da Brigada exige exatamente uma base ativa JUAZEIRO; encontradas: %',
            quantidade_bases;
    END IF;
END $$;

INSERT INTO ativos_inspecionaveis_sst (
    id, id_base_operacional, tipo, identificador_local, localizacao, atributos,
    origem, status_conferencia, possui_dado_suspeito, inicio_vigencia,
    versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), b.id, 'LUZ_EMERGENCIA', 'LUZ-' || v.sequencia,
       v.localizacao,
       jsonb_build_object(
           'sinalizacao', NULL,
           'limpezaEObstrucoes', NULL,
           'testeFuncionamento', NULL,
           'identificacaoTomadas', NULL)::text,
       'a929440d8bbd19bc7803ce50404c76621f84db53be51bbc917d426f39b620b71',
       'RASCUNHO', FALSE, CURRENT_DATE,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM bases_operacionais b
CROSS JOIN (VALUES
    (1, 'ADMINISTRATIVO INICIO DO CORREDOR'),
    (2, 'ADMINISTRATIVO MEIO DO CORREDOR'),
    (3, 'ADMINISTRATIVO FINAL DO CORREDOR'),
    (4, 'SALA DO SETOR DA GERENCIA OPERACIONAL'),
    (5, 'SALA DO SESMT'),
    (6, 'BANHEIRO FEMININO'),
    (7, 'BANHEIRO MASCULINO'),
    (8, 'SALA DA CONSTRUÇÃO'),
    (9, 'SALA DO RH'),
    (10, 'SALA DO FINANCEIRO'),
    (11, 'SALA DE TREINAMENTO'),
    (12, 'SALA DO MÉDICO'),
    (13, 'SALA DO COMERCIAL'),
    (14, 'SALA DA COORDENAÇÃO DO COMERCIAL'),
    (15, 'SALA DA LEITURA'),
    (16, 'SALA DO FECHAMENTO'),
    (17, 'SALA DO DESMPENHO'),
    (18, 'SALA DA LOGISTICA'),
    (19, 'SALA DA COORDENAÇÃO LOGISTICA'),
    (20, 'SALA DO ALMOXARIFADO DE MEDIDORES'),
    (21, 'SALA DA OFICINA'),
    (22, 'SALA DA PORTARIA')
) AS v(sequencia, localizacao)
WHERE UPPER(TRIM(b.nome)) = 'JUAZEIRO' AND b.ativo
ON CONFLICT (id_base_operacional, tipo, identificador_local) DO NOTHING;

INSERT INTO ativos_inspecionaveis_sst (
    id, id_base_operacional, tipo, identificador_local, localizacao, atributos,
    origem, status_conferencia, possui_dado_suspeito, inicio_vigencia,
    versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), b.id, 'EXTINTOR', 'EXT-' || v.posto,
       v.localizacao,
       jsonb_build_object(
           'agente', v.agente,
           'capacidade', v.capacidade,
           'tipo', v.tipo_extintor)::text,
       'a929440d8bbd19bc7803ce50404c76621f84db53be51bbc917d426f39b620b71',
       'RASCUNHO', v.posto = 10 AND v.tipo_extintor = '8', CURRENT_DATE,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM bases_operacionais b
CROSS JOIN (VALUES
    (1, 'PQS', '08 KG', 'BC', 'ADMINISTRATIVO INICIO CORREDOR'),
    (2, 'AP', '10 L', 'Agua', 'ADMINISTRATIVO INICIO CORREDOR'),
    (3, 'PQS', '08 KG', 'BC', 'ADMINISTRATIVO MEIO DO CORREDOR'),
    (4, 'AP', '10 L', 'Agua', 'ADMINISTRATIVO MEIO DO CORREDOR'),
    (5, 'PQS', '08 KG', 'BC', 'ADMINISTRATIVO FINAL DO CORREDOR'),
    (6, 'AP', '10 L', 'Agua', 'ADMINISTRATIVO FINAL DO CORREDOR'),
    (7, 'PQS', '08 KG', 'BC', 'ALMOXARIFADO FRENTE DO ADMINISTRATIVO'),
    (8, 'AP', '10 L', 'Agua', 'ALMOXARIFADO FRENTE DO ADMINISTRATIVO'),
    (9, 'PQS', '08 KG', 'BC', 'ALMOXARIFADO FINAL DO GALPÃO'),
    (10, 'AP', '10 L', '8', 'ALMOXARIFADO FINAL DO GALPÃO'),
    (11, 'PQS', '06 KG', 'BC', 'OFICINA SALÃO DE MANUTENÇÃO'),
    (12, 'AP', '10 L', 'Agua', 'OFICINA SALÃO DE MANUTENÇÃO'),
    (13, 'PQS', '08 KG', 'BC', 'OFICINA SALA ADMINISTRATIVO'),
    (14, 'PQS', '08 KG', 'BC', 'EM FRENTE A SALA DE TREINAMENTO')
) AS v(posto, agente, capacidade, tipo_extintor, localizacao)
WHERE UPPER(TRIM(b.nome)) = 'JUAZEIRO' AND b.ativo
ON CONFLICT (id_base_operacional, tipo, identificador_local) DO NOTHING;

-- Falhar é preferível a publicar um inventário parcial ou misturado com outra
-- origem sob os mesmos identificadores locais.
DO $$
DECLARE
    quantidade_luzes INTEGER;
    quantidade_extintores INTEGER;
    quantidade_suspeitos INTEGER;
BEGIN
    SELECT
        COUNT(*) FILTER (WHERE a.tipo = 'LUZ_EMERGENCIA'),
        COUNT(*) FILTER (WHERE a.tipo = 'EXTINTOR'),
        COUNT(*) FILTER (WHERE a.tipo = 'EXTINTOR' AND a.possui_dado_suspeito)
    INTO quantidade_luzes, quantidade_extintores, quantidade_suspeitos
    FROM ativos_inspecionaveis_sst a
    JOIN bases_operacionais b ON b.id = a.id_base_operacional
    WHERE UPPER(TRIM(b.nome)) = 'JUAZEIRO'
      AND b.ativo
      AND a.ativo
      AND a.origem = 'a929440d8bbd19bc7803ce50404c76621f84db53be51bbc917d426f39b620b71';

    IF quantidade_luzes <> 22 OR quantidade_extintores <> 14 OR quantidade_suspeitos <> 1 THEN
        RAISE EXCEPTION
            'Inventário da Brigada de JUAZEIRO incompleto: luzes=%, extintores=%, suspeitos=%',
            quantidade_luzes, quantidade_extintores, quantidade_suspeitos;
    END IF;
END $$;
