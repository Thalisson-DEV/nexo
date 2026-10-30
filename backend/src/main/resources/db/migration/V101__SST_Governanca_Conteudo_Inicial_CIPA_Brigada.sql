-- Conteúdo inicial estritamente próprio da governança de CIPA e Brigada.
--
-- Esta carga NÃO incorpora a matriz geral de SST, processos/funções, escolas
-- homologadas nem regras estaduais ainda não confirmadas. Os itens de Brigada
-- abaixo são modelos operacionais editáveis; não declaram conformidade legal.

-- =====================================================================
-- 1. Sanear os modelos de ata iniciais criados antes do suporte a DOCX
-- =====================================================================

-- A V95 publicou dois modelos sem arquivo porque a tabela de arquivos só surgiu
-- na V99. O domínio atual exige DOCX validado para publicar. Mantê-los publicados
-- criaria uma opção selecionável que falha apenas no momento da geração da ata.
-- Eles voltam a RASCUNHO para receber as referências da empresa pelo fluxo autorizado
-- de upload/storage e só então serem publicados novamente.
UPDATE modelos_ata_sst
SET status = 'RASCUNHO',
    publicado_em = NULL,
    id_usuario_publicacao = NULL,
    data_ultima_alteracao = CURRENT_TIMESTAMP,
    usuario_ultima_alteracao = 'MIGRATION_V101',
    versao = versao + 1
WHERE id IN (
    '51000000-0000-0000-0000-000000000001'::uuid,
    '51000000-0000-0000-0000-000000000002'::uuid)
  AND status = 'PUBLICADO'
  AND id_arquivo_docx IS NULL;

-- =====================================================================
-- 2. Categorias documentais específicas ainda não cobertas pela V96
-- =====================================================================

INSERT INTO sst_categorias_documento (
    id, codigo, revisao_categoria, nome, descricao, contexto, natureza, escopo_titular,
    jurisdicao, autoridade_emissora, identificador_fonte, edicao_fonte, url_fonte,
    vigencia_inicio, confirmada, data_confirmacao, usuario_confirmacao,
    exige_numero, exige_emissor, exige_data_emissao, exige_data_validade, sem_validade,
    obrigatoria, retencao_minima_anos, referencia_retencao,
    versao, data_criacao, usuario_criacao, ativo)
SELECT v.id, v.codigo, 1, v.nome, v.descricao, v.contexto, v.natureza, v.escopo,
       v.jurisdicao, v.autoridade, v.fonte, v.edicao, v.url, v.vigencia_inicio,
       TRUE, CURRENT_TIMESTAMP, '00000000-0000-0000-0000-000000000001'::uuid,
       FALSE, FALSE, TRUE, FALSE, TRUE, FALSE,
       v.retencao_anos, v.referencia_retencao,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM (VALUES
    ('71000000-0000-0000-0000-000000000001'::uuid,
     'CIPA_ATA_ELEICAO_APURACAO', 'Ata de eleição e apuração da CIPA',
     'Registro da eleição, apuração e resultado da representação eleita.',
     'CIPA', 'REGISTRO_CIPA', 'CORPO',
     'BR', 'MTE', 'NR-5', '2023', 'https://www.gov.br/trabalho-e-emprego/',
     DATE '2023-03-20', 5, 'NR-5'),
    ('71000000-0000-0000-0000-000000000002'::uuid,
     'CIPA_ATA_POSSE', 'Ata de posse da CIPA',
     'Registro da posse e do início da composição da CIPA.',
     'CIPA', 'REGISTRO_CIPA', 'CORPO',
     'BR', 'MTE', 'NR-5', '2023', 'https://www.gov.br/trabalho-e-emprego/',
     DATE '2023-03-20', 5, 'NR-5'),
    ('71000000-0000-0000-0000-000000000003'::uuid,
     'CIPA_CALENDARIO_REUNIOES', 'Calendário de reuniões da CIPA',
     'Calendário aprovado para as reuniões da composição.',
     'CIPA', 'REGISTRO_CIPA', 'CORPO',
     'BR', 'MTE', 'NR-5', '2023', 'https://www.gov.br/trabalho-e-emprego/',
     DATE '2023-03-20', 5, 'NR-5'),
    ('71000000-0000-0000-0000-000000000004'::uuid,
     'CIPA_PLANO_TRABALHO', 'Plano de trabalho da CIPA',
     'Plano de trabalho e acompanhamento das ações da composição.',
     'CIPA', 'REGISTRO_CIPA', 'CORPO',
     'BR', 'MTE', 'NR-5', '2023', 'https://www.gov.br/trabalho-e-emprego/',
     DATE '2023-03-20', 5, 'NR-5'),
    ('71000000-0000-0000-0000-000000000005'::uuid,
     'CIPA_SIPAT', 'Registros da SIPAT',
     'Planejamento, divulgação e evidências da SIPAT vinculada à composição.',
     'CIPA', 'REGISTRO_CIPA', 'CORPO',
     'BR', 'MTE', 'NR-5', '2023', 'https://www.gov.br/trabalho-e-emprego/',
     DATE '2023-03-20', 5, 'NR-5'),
    ('71000000-0000-0000-0000-000000000006'::uuid,
     'BRIGADA_ATA_REUNIAO', 'Ata de reunião da Brigada',
     'Cópia assinada e evidências documentais de reunião da Brigada.',
     'BRIGADA', 'REGISTRO_BRIGADA', 'REUNIAO',
     NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL),
    ('71000000-0000-0000-0000-000000000007'::uuid,
     'BRIGADA_PLANO_EMERGENCIA', 'Plano de emergência da base',
     'Categoria operacional para o plano ou projeto de emergência da edificação; requisitos legais dependem de fonte confirmada.',
     'BRIGADA', 'REGISTRO_BRIGADA', 'BASE',
     NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL),
    ('71000000-0000-0000-0000-000000000008'::uuid,
     'BRIGADA_REGISTRO_SIMULADO', 'Registro de simulado da Brigada',
     'Planejamento, execução, participantes e resultados de simulado.',
     'BRIGADA', 'REGISTRO_BRIGADA', 'CORPO',
     NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL),
    ('71000000-0000-0000-0000-000000000009'::uuid,
     'BRIGADA_REGISTRO_INSPECAO', 'Registro de inspeção da Brigada',
     'Relatório ou evidência de inspeção preventiva vinculada à base.',
     'BRIGADA', 'REGISTRO_BRIGADA', 'BASE',
     NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL),
    ('71000000-0000-0000-0000-00000000000a'::uuid,
     'BRIGADA_REGISTRO_MANUTENCAO', 'Registro de manutenção de equipamentos',
     'Evidência de manutenção dos equipamentos de prevenção e combate a incêndio da base.',
     'BRIGADA', 'REGISTRO_BRIGADA', 'BASE',
     NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL)
) AS v(id, codigo, nome, descricao, contexto, natureza, escopo,
       jurisdicao, autoridade, fonte, edicao, url, vigencia_inicio,
       retencao_anos, referencia_retencao)
ON CONFLICT (codigo, revisao_categoria) DO NOTHING;

-- =====================================================================
-- 3. Catálogo mínimo de treinamentos próprios dos dois corpos
-- =====================================================================

-- Somente o catálogo é criado. Carga horária, recorrência, modalidade e
-- exigência por papel dependem de uma fonte versionada e confirmada.
INSERT INTO sst_treinamentos_governanca (
    id, codigo, nome, nome_normalizado, descricao, aplica_cipa, aplica_brigada,
    versao, data_criacao, usuario_criacao, ativo)
VALUES
    ('71100000-0000-0000-0000-000000000001'::uuid,
     'CIPA_FORMACAO_MEMBROS', 'Formação de membros da CIPA',
     'formacao de membros da cipa',
     'Catálogo específico da CIPA. Critérios de atendimento são definidos somente por exigência com fonte confirmada.',
     TRUE, FALSE, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71100000-0000-0000-0000-000000000002'::uuid,
     'BRIGADA_FORMACAO', 'Formação de brigadista',
     'formacao de brigadista',
     'Catálogo específico da Brigada. Critérios de atendimento são definidos somente por exigência com fonte confirmada.',
     FALSE, TRUE, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- =====================================================================
-- 4. Modelos iniciais editáveis de fiscalização da Brigada
-- =====================================================================

INSERT INTO formularios_inspecao (
    id, titulo, descricao, versao, data_criacao, usuario_criacao, ativo)
VALUES
    ('71200000-0000-0000-0000-000000000001'::uuid,
     'Brigada — Luzes de emergência e tomadas',
     'Modelo inicial editável para fiscalizar luzes de emergência e identificação de voltagem das tomadas.',
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71200000-0000-0000-0000-000000000002'::uuid,
     'Brigada — Extintores de incêndio',
     'Modelo inicial editável para fiscalizar os extintores cadastrados na base.',
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO grupos_pergunta (
    id, id_formulario, titulo, ordem, versao, data_criacao, usuario_criacao, ativo)
VALUES
    ('71300000-0000-0000-0000-000000000001'::uuid,
     '71200000-0000-0000-0000-000000000001'::uuid,
     'Verificações do ponto', 1, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71300000-0000-0000-0000-000000000002'::uuid,
     '71200000-0000-0000-0000-000000000002'::uuid,
     'Verificações do extintor', 1, 0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO perguntas_inspecao (
    id, id_grupo, enunciado, tipo_resposta, obrigatoria, ordem,
    versao, data_criacao, usuario_criacao, ativo)
VALUES
    ('71400000-0000-0000-0000-000000000001'::uuid,
     '71300000-0000-0000-0000-000000000001'::uuid,
     'A sinalização da luz de emergência está adequada?', 'MULTIPLA_ESCOLHA', TRUE, 1,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-000000000002'::uuid,
     '71300000-0000-0000-0000-000000000001'::uuid,
     'O ponto está limpo e livre de obstruções?', 'MULTIPLA_ESCOLHA', TRUE, 2,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-000000000003'::uuid,
     '71300000-0000-0000-0000-000000000001'::uuid,
     'A luz de emergência funciona no teste?', 'MULTIPLA_ESCOLHA', TRUE, 3,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-000000000004'::uuid,
     '71300000-0000-0000-0000-000000000001'::uuid,
     'A identificação de voltagem das tomadas está adequada?', 'MULTIPLA_ESCOLHA', TRUE, 4,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-000000000005'::uuid,
     '71300000-0000-0000-0000-000000000002'::uuid,
     'Qual é a data da próxima recarga?', 'DATA', FALSE, 1,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-000000000006'::uuid,
     '71300000-0000-0000-0000-000000000002'::uuid,
     'Qual é a data do próximo teste hidrostático?', 'DATA', FALSE, 2,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-000000000007'::uuid,
     '71300000-0000-0000-0000-000000000002'::uuid,
     'A mangueira e o difusor estão conservados?', 'MULTIPLA_ESCOLHA', TRUE, 3,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-000000000008'::uuid,
     '71300000-0000-0000-0000-000000000002'::uuid,
     'A etiqueta de inspeção existe e está legível?', 'MULTIPLA_ESCOLHA', TRUE, 4,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-000000000009'::uuid,
     '71300000-0000-0000-0000-000000000002'::uuid,
     'O lacre está íntegro e não violado?', 'MULTIPLA_ESCOLHA', TRUE, 5,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-00000000000a'::uuid,
     '71300000-0000-0000-0000-000000000002'::uuid,
     'O manômetro está na faixa verde?', 'MULTIPLA_ESCOLHA', TRUE, 6,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-00000000000b'::uuid,
     '71300000-0000-0000-0000-000000000002'::uuid,
     'O extintor e o suporte estão conservados?', 'MULTIPLA_ESCOLHA', TRUE, 7,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-00000000000c'::uuid,
     '71300000-0000-0000-0000-000000000002'::uuid,
     'A sinalização de parede está adequada?', 'MULTIPLA_ESCOLHA', TRUE, 8,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71400000-0000-0000-0000-00000000000d'::uuid,
     '71300000-0000-0000-0000-000000000002'::uuid,
     'A sinalização de piso está adequada?', 'MULTIPLA_ESCOLHA', TRUE, 9,
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (id) DO NOTHING;

-- A fonte não definiu enumeração nem gravidade. Este modelo operacional oferece
-- quatro estados explícitos, preserva NAO_VERIFICADO e usa MEDIA apenas como
-- padrão editável da constatação gerada por uma resposta não conforme.
INSERT INTO opcoes_resposta (
    id, id_pergunta, texto, gera_desvio, ordem, gravidade,
    versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), p.id, o.texto, o.gera_desvio, o.ordem, o.gravidade,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM perguntas_inspecao p
CROSS JOIN (VALUES
    ('Conforme', FALSE, 1, NULL::varchar),
    ('Não conforme', TRUE, 2, 'MEDIA'::varchar),
    ('Não aplicável', FALSE, 3, NULL::varchar),
    ('Não verificado', FALSE, 4, NULL::varchar)
) AS o(texto, gera_desvio, ordem, gravidade)
WHERE p.id IN (
    '71400000-0000-0000-0000-000000000001'::uuid,
    '71400000-0000-0000-0000-000000000002'::uuid,
    '71400000-0000-0000-0000-000000000003'::uuid,
    '71400000-0000-0000-0000-000000000004'::uuid,
    '71400000-0000-0000-0000-000000000007'::uuid,
    '71400000-0000-0000-0000-000000000008'::uuid,
    '71400000-0000-0000-0000-000000000009'::uuid,
    '71400000-0000-0000-0000-00000000000a'::uuid,
    '71400000-0000-0000-0000-00000000000b'::uuid,
    '71400000-0000-0000-0000-00000000000c'::uuid,
    '71400000-0000-0000-0000-00000000000d'::uuid
);

INSERT INTO modelos_checklist_governanca_sst (
    id, id_formulario, tipo_corpo, nome, orientacao, referencia_normativa,
    versao, data_criacao, usuario_criacao, ativo)
VALUES
    ('71500000-0000-0000-0000-000000000001'::uuid,
     '71200000-0000-0000-0000-000000000001'::uuid,
     'BRIGADA', 'Modelo inicial — Luzes de emergência e tomadas',
     'Confirme o ponto e a localização antes de preencher. Não converta campo sem inspeção em conforme; use Não verificado.',
     'Referência operacional da empresa: modelo de fiscalização de luzes de emergência, voltagem e extintores.',
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE),
    ('71500000-0000-0000-0000-000000000002'::uuid,
     '71200000-0000-0000-0000-000000000002'::uuid,
     'BRIGADA', 'Modelo inicial — Extintores de incêndio',
     'Confirme o posto, a localização e os dados cadastrais antes de preencher. Datas ausentes permanecem sem resposta.',
     'Referência operacional da empresa: modelo de fiscalização de luzes de emergência, voltagem e extintores.',
     0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE)
ON CONFLICT (id) DO NOTHING;
