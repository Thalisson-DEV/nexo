-- =====================================================================
-- V89: Cadastros da governança de acidentalidade do SST
--
-- Primeira fase do módulo de eventos (acidentes, quase-acidentes,
-- incidentes, danos materiais e doenças ocupacionais): os cadastros que
-- o evento vai referenciar.
--
-- Duas tabelas, com papéis diferentes:
--
--   * atividades_sst — a natureza do que estava sendo feito (trajeto,
--     detonação, poda). Tem gestor responsável. O evento vai COPIAR
--     atividade e gestor no momento do registro, e é por isso que trocar
--     o gestor aqui não mexe em nenhum evento passado.
--
--   * catalogos_sst — os quatro vocabulários de classificação do evento,
--     discriminados por `tipo`. Dividem uma tabela porque têm forma
--     idêntica; um vocabulário novo passa a ser uma constante no enum, e
--     não uma migration. O evento REFERENCIA estas entradas (sem cópia):
--     são vocabulário estável, e corrigir um nome aqui deve corrigi-lo em
--     todo lugar.
--
-- Referências a colaboradores e processos ficam sem FK, preservando o
-- isolamento entre módulos (mesmo padrão de inspecoes_seguranca).
-- =====================================================================

-- ═══════════════════════════════════════════════════════════════════════
-- Atividades
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE atividades_sst (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    -- Sem acento e em minúsculas: é sobre esta coluna que a unicidade e a
    -- busca comparam, gravada pelo domínio com a mesma normalização usada
    -- no termo de pesquisa.
    nome_normalizado VARCHAR(120) NOT NULL,
    descricao VARCHAR(500),

    -- Gestor responsável. NULO é permitido de propósito: a carga inicial
    -- abaixo cadastra as atividades do contrato antes de se saber quem
    -- responde por cada uma. A exigência não some — ela passa para o
    -- registro do evento, que cobra o gestor quando a atividade não tem.
    id_gestor UUID,
    id_processo UUID,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE
);

-- Unicidade apenas entre as ativas: uma atividade desativada não deve
-- impedir que o nome seja reutilizado.
CREATE UNIQUE INDEX ux_atividade_sst_nome_ativa
    ON atividades_sst (nome_normalizado) WHERE ativo;

CREATE INDEX ix_atividade_sst_gestor ON atividades_sst (id_gestor);
CREATE INDEX ix_atividade_sst_processo ON atividades_sst (id_processo);

-- ═══════════════════════════════════════════════════════════════════════
-- Catálogos
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE catalogos_sst (
    id UUID PRIMARY KEY,
    -- CLASSIFICACAO_EVENTO | LOCAL_LESAO | NATUREZA_LESAO | AGENTE_CAUSADOR
    tipo VARCHAR(30) NOT NULL,
    nome VARCHAR(120) NOT NULL,
    nome_normalizado VARCHAR(120) NOT NULL,
    descricao VARCHAR(255),
    -- Código da tabela externa correspondente (NBR 14280, eSocial), quando houver.
    codigo VARCHAR(30),
    ordem INTEGER NOT NULL DEFAULT 0,

    versao BIGINT DEFAULT 0,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_ultima_alteracao TIMESTAMP WITH TIME ZONE,
    usuario_criacao VARCHAR(255),
    usuario_ultima_alteracao VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_desativacao TIMESTAMP WITH TIME ZONE,

    CONSTRAINT ck_catalogo_sst_ordem_nao_negativa CHECK (ordem >= 0)
);

-- O nome é único DENTRO do vocabulário: "Mão direita" pode existir como
-- local de lesão sem impedir um agente causador de mesmo nome.
CREATE UNIQUE INDEX ux_catalogo_sst_tipo_nome_ativo
    ON catalogos_sst (tipo, nome_normalizado) WHERE ativo;

CREATE INDEX ix_catalogo_sst_tipo_ativo ON catalogos_sst (tipo, ativo);

-- ═══════════════════════════════════════════════════════════════════════
-- Carga inicial — atividades
--
-- As dezesseis primeiras são as atividades em uso hoje. As demais são
-- sugestões do mesmo contexto (distribuição de energia): quem não as
-- executa desativa, e o registro histórico de quem as usou fica intacto.
-- ═══════════════════════════════════════════════════════════════════════

INSERT INTO catalogos_sst (id, tipo, nome, nome_normalizado, descricao, ordem,
                           versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), 'CLASSIFICACAO_EVENTO', v.nome,
       immutable_unaccent(lower(v.nome)), v.descricao, v.ordem,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM (VALUES
    -- Trajeto
    ('TRAJETO SEM AFASTAMENTO', NULL::text, 10),
    ('TRAJETO COM AFASTAMENTO', NULL, 20),

    -- Incidentes. SEP = Sistema Elétrico de Potência (NR-10).
    ('INCIDENTE', NULL, 30),
    ('INCIDENTE + SEP NÃO AGENTE', NULL, 40),
    ('INCIDENTE + SEP', NULL, 50),

    -- Danos materiais
    ('DANOS MATERIAIS NÃO AGENTE', NULL, 60),
    ('DANOS MATERIAIS AGENTE', NULL, 70),

    -- Siglas de veículo da contratante. A descrição fica em branco de
    -- propósito: o significado é dela, e o SESMT preenche pela tabela
    -- oficial em vez de herdar um palpite gravado aqui.
    ('VNA', NULL, 80),
    ('VA1', NULL, 90),
    ('VA2', NULL, 100),
    ('VA3', NULL, 110),

    -- Escala internacional de gravidade da lesão, do mais leve ao óbito.
    ('FAC', 'First Aid Case — atendimento de primeiros socorros, sem tratamento médico', 120),
    ('MTC', 'Medical Treatment Case — exigiu tratamento médico, sem restrição nem afastamento', 130),
    ('RWC', 'Restricted Work Case — retorno ao trabalho com restrição de função', 140),
    ('LTI', 'Lost Time Injury — acidente com afastamento', 150),
    ('FATAL', 'Evento com óbito', 160)
) AS v(nome, descricao, ordem)
ON CONFLICT (tipo, nome_normalizado) WHERE ativo DO NOTHING;

INSERT INTO atividades_sst (id, nome, nome_normalizado, descricao,
                            versao, data_criacao, usuario_criacao, ativo)
SELECT gen_random_uuid(), v.nome, immutable_unaccent(lower(v.nome)), NULL,
       0, CURRENT_TIMESTAMP, 'SISTEMA', TRUE
FROM (VALUES
    -- Em uso hoje
    ('Abertura de Cava'),
    ('Trajeto'),
    ('Implantação de Poste'),
    ('Intercalação de Poste'),
    ('Substituição de Poste'),
    ('Montagem de Estrutura'),
    ('Montagem de Equipamento'),
    ('Coleta de Leitura'),
    ('Entrega de Fatura'),
    ('Corte e Ligação'),
    ('Varredura'),
    ('Poda'),
    ('Detonação'),
    ('Carga e Descarga'),
    ('Lançamento de Cabo'),
    ('Deslocamento a Serviço'),

    -- Sugestões do mesmo contexto operacional
    ('Inspeção de Rede'),
    ('Manutenção em Linha Viva'),
    ('Manobra em Rede Energizada'),
    ('Substituição de Medidor'),
    ('Instalação de Ramal de Ligação'),
    ('Trabalho em Altura'),
    ('Içamento com Munck'),
    ('Sinalização e Isolamento de Área'),
    ('Atendimento a Emergência')
) AS v(nome)
ON CONFLICT (nome_normalizado) WHERE ativo DO NOTHING;
